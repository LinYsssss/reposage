package com.example.codereview.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.codereview.ai.AiCallLogService;
import com.example.codereview.knowledge.KnowledgeChunk;
import com.example.codereview.knowledge.KnowledgeChunkRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class RagServiceTest {

    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final EmbeddingJson embeddingJson = mock(EmbeddingJson.class);
    private final AiCallLogService aiCallLogService = mock(AiCallLogService.class);

    @SuppressWarnings("unchecked")
    private RagService newService() {
        ObjectProvider<JdbcTemplate> jdbcProvider = mock(ObjectProvider.class);
        when(jdbcProvider.getIfAvailable()).thenReturn(null);
        // full-context mode keeps the test off the embedding/vector path.
        return new RagService(chunks, embeddingClient, embeddingJson, jdbcProvider, aiCallLogService,
                "memory", 5, true, 6000);
    }

    private static KnowledgeChunk chunk(Long projectId, Long documentId, String source, String content) {
        return new KnowledgeChunk(documentId, projectId, "MARKDOWN", source, 0, content, null);
    }

    @Test
    void selectedDocuments_useDocumentScopedQueryAndOnlyReturnThatContent() {
        RagService service = newService();
        when(chunks.findByProjectIdAndDocumentIdIn(eq(1L), eq(List.of(10L))))
                .thenReturn(List.of(chunk(1L, 10L, "a.md", "project-A-selected-doc")));

        String context = service.buildContext(1L, "query", List.of(10L));

        assertThat(context).contains("project-A-selected-doc");
        // Document-scoped retrieval, never the project-wide finder.
        verify(chunks).findByProjectIdAndDocumentIdIn(eq(1L), eq(List.of(10L)));
        verify(chunks, never()).findByProjectId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void noDocumentSelection_usesProjectScopedQueryForThatProjectOnly() {
        RagService service = newService();
        when(chunks.findByProjectId(1L))
                .thenReturn(List.of(chunk(1L, 10L, "a.md", "project-A-content")));

        String context = service.buildContext(1L, "query", null);

        assertThat(context).contains("project-A-content");
        verify(chunks).findByProjectId(1L);
        verify(chunks, never()).findByProjectIdAndDocumentIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
