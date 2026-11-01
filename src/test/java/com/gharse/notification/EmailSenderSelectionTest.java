package com.gharse.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailSenderSelectionTest {

    @Configuration
    static class MailMock {
        @Bean
        JavaMailSender javaMailSender() {
            return mock(JavaMailSender.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailMock.class, SimulatedEmailSender.class, SmtpEmailSender.class);

    @Test
    void switchOffUsesTheSimulatedSender() {
        runner.withPropertyValues("app.notifications.email.enabled=false").run(ctx -> {
            assertThat(ctx).hasSingleBean(SimulatedEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
        });
    }

    @Test
    void missingSwitchDefaultsToTheSimulatedSender() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(SimulatedEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
        });
    }

    @Test
    void switchOnWithCredentialsUsesSmtp() {
        runner.withPropertyValues(
                "app.notifications.email.enabled=true",
                "app.notifications.email.from=support.gharse@gmail.com",
                "spring.mail.password=secret").run(ctx -> {
            assertThat(ctx).hasSingleBean(SmtpEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SimulatedEmailSender.class);
        });
    }

    @Test
    void switchOnWithoutCredentialsRefusesToStart() {
        runner.withPropertyValues("app.notifications.email.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}