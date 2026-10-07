package io.roadmap.tasktrackeremailsender.kafka;

import io.roadmap.tasktrackeremailsender.dto.KafkaCreateUserMessage;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@KafkaListener(topics = "EMAIL_SENDING_TASKS", groupId = "my-consumer-group")
public class KafkaMessageListener {

    @KafkaHandler
    public void onUserCreated(KafkaCreateUserMessage message) {
        System.out.println("User created: " + message);
    }


    //TODO посмотреть как работает с неизвестным DTO
//    @KafkaHandler(isDefault = true)
//    public void onUnknown(Object message) {
//        System.out.println("Unknown message type: " + message);
//    }
}
