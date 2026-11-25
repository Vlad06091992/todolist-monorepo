package io.roadmap.tasktracker.emailsender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskTrackerEmailSenderApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskTrackerEmailSenderApplication.class, args);
	}
}
