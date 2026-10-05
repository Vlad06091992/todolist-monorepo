package io.roadmap.tasktrackeremailsender.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component

public class KafkaMessageListener {

    public KafkaMessageListener(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    private final KafkaTemplate<String, String> kafkaTemplate;

    // Simple listener consuming plain string messages
    @KafkaListener(topics = "my-topic", groupId = "my-consumer-group")
    public void listen(String message) {
        System.out.println("Received message mailer: " + message);
//        kafkaTemplate.send("my-topic-2", message);
        kafkaTemplate.send("my-topic-2", message + "from mailer");
    }
}
