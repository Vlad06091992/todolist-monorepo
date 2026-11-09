package io.roadmap.scheduler;/*
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


import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.osgi.annotation.bundle.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
//
///**
// * Sample producer application using Reactive API for Kafka.
// * To run sample producer
// * <ol>
// *   <li> Start Zookeeper and Kafka server
// *   <li> Update {@link #BOOTSTRAP_SERVERS} and {@link #TOPIC} if required
// *   <li> Create Kafka topic {@link #TOPIC}
// *   <li> Run {@link KafkaService} as Java application with all dependent jars in the CLASSPATH (eg. from IDE).
// *   <li> Shutdown Kafka server and Zookeeper when no longer required
// * </ol>
// */

@RequiredArgsConstructor
@Service
public class KafkaService {

    private final KafkaSender sender;
    private static final Logger log = LoggerFactory.getLogger(KafkaService.class.getName());


    public <T> Mono<Void> sendMessage(String topic, T message) {
        UUID uuid = UUID.randomUUID();
        String uuidStr = uuid.toString();
        return sender.send(makeRecord(topic,message, uuidStr))
                .doOnNext(r -> log.info("отправка мок сообщения {}", r.toString()))
                .doOnError(e -> log.error("Ошибка при отправке в топик {}", e))
                .then();
    }

    public void close() {
        sender.close();
    }

    private <T> Mono<SenderRecord<Integer, T, String>> makeRecord(String topic, T payload, String correlationId){
        ProducerRecord<Integer, T> producerRecord = new ProducerRecord<>(topic, payload);

        producerRecord.headers().add(
                "CORRELATION_ID",
                correlationId.getBytes(StandardCharsets.UTF_8)
        );

        return   Mono.just(
                SenderRecord.create(
                        producerRecord,
                        null
                )
        );
    }
}