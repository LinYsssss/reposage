package com.example.codereview.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.codereview.review.ReviewProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewTaskConsumerTest {

    @Mock ReviewProcessor reviewProcessor;
    @Mock ReviewTaskPublisher publisher;
    @Mock MqLogService mqLogService;

    private static final int MAX_RETRY = 3;

    @Test
    void belowRetryLimit_reschedulesWithIncrementedRetryAndNoDeadLetter() {
        ReviewTaskConsumer consumer = new ReviewTaskConsumer(reviewProcessor, publisher, mqLogService, MAX_RETRY);
        ReviewTaskMessage message = new ReviewTaskMessage("m1", 5L, 7L, "abc", 1, java.time.Instant.now());
        doThrow(new RuntimeException("transient")).when(reviewProcessor).process(5L);

        consumer.consume(message);

        ArgumentCaptor<ReviewTaskMessage> captor = ArgumentCaptor.forClass(ReviewTaskMessage.class);
        verify(publisher).publishDelayed(captor.capture());
        assertThat(captor.getValue().retryCount()).isEqualTo(2);
        assertThat(captor.getValue().taskId()).isEqualTo(5L);
        verify(publisher, never()).publishDead(org.mockito.ArgumentMatchers.any(), anyString());
        verify(reviewProcessor, never()).markDead(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void atRetryLimit_marksDeadAndDeadLetters() {
        ReviewTaskConsumer consumer = new ReviewTaskConsumer(reviewProcessor, publisher, mqLogService, MAX_RETRY);
        ReviewTaskMessage message = new ReviewTaskMessage("m1", 5L, 7L, "abc", MAX_RETRY, java.time.Instant.now());
        doThrow(new RuntimeException("boom")).when(reviewProcessor).process(5L);

        consumer.consume(message);

        verify(reviewProcessor).markDead(eq(5L), anyString());
        verify(publisher).publishDead(eq(message), anyString());
        verify(publisher, never()).publishDelayed(org.mockito.ArgumentMatchers.any());
    }
}
