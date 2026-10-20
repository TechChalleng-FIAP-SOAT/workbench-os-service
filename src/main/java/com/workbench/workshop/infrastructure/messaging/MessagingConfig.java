package com.workbench.workshop.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Cria os clients SNS/SQS apenas quando {@code messaging.enabled=true}.
 *
 * <p>Quando um {@code endpoint} é informado (LocalStack), aponta os clients para
 * ele e usa credenciais fake; sem endpoint, usa a resolução padrão da AWS
 * (variáveis de ambiente / IAM role) e o endpoint público do serviço.
 */
@Configuration
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@org.springframework.scheduling.annotation.EnableScheduling
public class MessagingConfig {

    @Bean
    public SnsClient snsClient(MessagingProperties props) {
        var builder = SnsClient.builder().region(Region.of(props.getRegion()));
        applyEndpoint(props, builder::endpointOverride, builder::credentialsProvider);
        return builder.build();
    }

    @Bean
    public SqsClient sqsClient(MessagingProperties props) {
        var builder = SqsClient.builder().region(Region.of(props.getRegion()));
        applyEndpoint(props, builder::endpointOverride, builder::credentialsProvider);
        return builder.build();
    }

    private void applyEndpoint(MessagingProperties props,
                               java.util.function.Consumer<URI> endpointSetter,
                               java.util.function.Consumer<software.amazon.awssdk.auth.credentials.AwsCredentialsProvider> credsSetter) {
        if (props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            endpointSetter.accept(URI.create(props.getEndpoint()));
            credsSetter.accept(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")));
        } else {
            credsSetter.accept(DefaultCredentialsProvider.create());
        }
    }
}
