package io.roadmap.todolistmonorepo.kafka;/*
 * Copyright (c) 2016-2022 VMware Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

/**
 * Sample producer application using Reactive API for Kafka.
 * To run sample producer
 * <ol>
 *   <li> Start Zookeeper and Kafka server
 *   <li> Update {@link #BOOTSTRAP_SERVERS} and {@link #TOPIC} if required
 *   <li> Create Kafka topic {@link #TOPIC}
 *   <li> Run {@link KafkaService} as Java application with all dependent jars in the CLASSPATH (eg. from IDE).
 *   <li> Shutdown Kafka server and Zookeeper when no longer required
 * </ol>
 */

@RequiredArgsConstructor
@Service
public class KafkaService {

    private final KafkaSender sender;
    private static final Logger log = LoggerFactory.getLogger(KafkaService.class.getName());

//    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
//    private static final String TOPIC = "demo-topic";

//    private final KafkaSender<Integer, String> sender;
//    private final DateTimeFormatter dateFormat;

//    public KafkaService(String bootstrapServers) {
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

//    public void sendMessages(String topic, int count, CountDownLatch latch) throws InterruptedException {
//        sender.<Integer>send(Flux.range(1, count)
//                        .map(i -> SenderRecord.create(new ProducerRecord<>(topic, i, "Message_" + i), i)))
//                .doOnError(e -> log.error("Send failed", e))
//                .subscribe(r -> {
//                    RecordMetadata metadata = r.recordMetadata();
//                    Instant timestamp = Instant.ofEpochMilli(metadata.timestamp());
//                    System.out.printf("Message %d sent successfully, topic-partition=%s-%d offset=%d timestamp=%s\n",
//                            r.correlationMetadata(),
//                            metadata.topic(),
//                            metadata.partition(),
//                            metadata.offset(),
//                            dateFormat.format(timestamp));
//                    latch.countDown();
//                });
//    }


    public <T> Mono<Void> sendMessage(String topic, T message) {
        return sender.send(makeRecord(topic,message))
                .doOnNext(r -> log.info("отправка сообщения о регистрации пользователя {}", r.toString()))
                .doOnError(e -> log.error("Ошибка при отправке в топик {}", e))
                .then();
    }

    public void close() {
        sender.close();
    }

    private <T> Mono<SenderRecord<Integer, T, Integer>> makeRecord(String topic, T payload){
        return   Mono.just(
                SenderRecord.create(
                        new ProducerRecord<>(topic, payload),
                        null
                )
        );
    }
}