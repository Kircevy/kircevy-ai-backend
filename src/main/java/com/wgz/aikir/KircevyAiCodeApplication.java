package com.wgz.aikir;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.wgz.aikir.mapper")
public class KircevyAiCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KircevyAiCodeApplication.class, args);
    }

}
