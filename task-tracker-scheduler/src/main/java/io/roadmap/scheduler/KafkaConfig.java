package io.roadmap.scheduler;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Configuration
public class KafkaConfig {


//    public SampleConsumer(String bootstrapServers) {
//
//        Map<String, Object> props = new HashMap<>();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "sample-consumer");
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sample-group");
//        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
//        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//        receiverOptions = ReceiverOptions.create(props);
//        dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss:SSS z dd MMM yyyy");
//    }


//    public SampleProducer(String bootstrapServers) {
//
//        Map<String, Object> props = new HashMap<>();
//        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
//        props.put(ProducerConfig.ACKS_CONFIG, "all");
//        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
//        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        SenderOptions<Integer, String> senderOptions = SenderOptions.create(props);
//
//        sender = KafkaSender.create(senderOptions);
//        dateFormat = DateTimeFormatter.ofPattern("HH:mm:ss:SSS z dd MMM yyyy");
//    }


    @Bean
    public KafkaSender<Integer, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<Integer, Object> senderOptions =
                SenderOptions.create(kafkaProperties.buildProducerProperties());

        return KafkaSender.create(senderOptions);
    }

    @Bean
    public ReceiverOptions<String, Object> receiverOptions(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, Object> receiverOptions =
                ReceiverOptions.create(kafkaProperties.buildConsumerProperties());

        return receiverOptions;
    }
}
