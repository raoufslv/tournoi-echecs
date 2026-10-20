package me.raouf.chess.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiveServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LiveServiceApplication.class, args);
	}

}
