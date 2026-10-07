package io.roadmap.todolistmonorepo.configuration;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<Integer, Object> senderOptions =
                SenderOptions.create(kafkaProperties.buildProducerProperties());

        return KafkaSender.create(senderOptions);
    }
}
