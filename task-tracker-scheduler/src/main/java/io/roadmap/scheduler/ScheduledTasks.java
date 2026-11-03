package io.roadmap.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class ScheduledTasks {

    @Autowired
    TasksService tasksService;

    @Autowired
     KafkaExample kafkaExample;

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Scheduled(fixedRate = 5000)
    public void reportCurrentTime() {
        log.info("The time is now {}", dateFormat.format(new Date()));
    }


//    @Scheduled(fixedRate = 10000)
    public void reauest() {
        System.out.println("mock request");
       Flux<TaskResponse> res = tasksService.sendRequest("7b2f2dee-f009-42b2-b77d-1046a71cb593", "2025-11-03T14:30");

       //block для отладки
       List<TaskResponse> hz = res.collectList().block();
        log.info("ответ через block(): {}", hz);


        /// ///////// то что придёт сюда
        /*
        [
        {
            "id": "85cbd5a1-7f56-4945-ac72-9b2298abadc2",
                "user": {
            "id": "7b2f2dee-f009-42b2-b77d-1046a71cb593",
                    "login": "vlad"
        },
            "description": "попить молока",
                "isFinished": true,
                "createdAt": "2026-09-27T10:08:11.255099"
        },
        {
            "id": "2c0c5bad-079c-49ba-80cf-381c35ef7438",
                "user": {
            "id": "7b2f2dee-f009-42b2-b77d-1046a71cb593",
                    "login": "vlad"
        },
            "description": "почесаться",
                "isFinished": true,
                "createdAt": "2026-11-03T09:58:05.629439"
        },
        {
            "id": "65a7ec3d-7432-4c73-a8c7-93aae308f1ae",
                "user": {
            "id": "7b2f2dee-f009-42b2-b77d-1046a71cb593",
                    "login": "vlad"
        },
            "description": "попить квас",
                "isFinished": true,
                "createdAt": "2026-11-03T09:57:57.114716"
        }
]

*/

        //то что принимает summariation на входе

        /*
        {
    "payload": [
        {
            "login": "Валера",
            "completedLast24h": [
                "Позавтракать",
                "Пообедать",
                "Пооужинать"
            ],
            "activeTasks": 10
        }
    ]
}
         */

    }

@Scheduled(fixedRate = 10000)
public void sendMessageToTopic() {
    kafkaExample.send();
//    System.out.println("mock request");
//        tasksService.sendRequest();
}

}