package dev.azn9.d4jtest;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.monetization.EntitlementCreateEvent;
import discord4j.core.event.domain.monetization.EntitlementDeleteEvent;
import discord4j.core.event.domain.monetization.EntitlementUpdateEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.monetization.Entitlement;
import discord4j.discordjson.json.CreateTestEntitlementRequest;
import discord4j.discordjson.json.EntitlementData;
import discord4j.discordjson.json.SkuData;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.service.MonetizationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.tools.agent.ReactorDebugAgent;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class MonetizationTest {

    // TODO: Replace with your own values
    private static final String DISCORD_TOKEN = "";
    private static final Long TEST_USER_ID = 0L;
    private static final Long TEST_TEXT_CHANNEL_ID = 0L;
    private static final String SKU_NAME = "";

    private static GatewayDiscordClient gatewayDiscordClient;
    private static Long applicationId;
    private static MonetizationService monetizationService;
    private static long skuId;
    private static long testEntitlementId;

    @BeforeAll
    static void initialize() {
        ReactorDebugAgent.init();

        // Initialize the gateway client
        MonetizationTest.gatewayDiscordClient = DiscordClientBuilder.create(MonetizationTest.DISCORD_TOKEN)
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.nonPrivileged())
                .login()
                .block();

        Assertions.assertNotNull(MonetizationTest.gatewayDiscordClient);

        // Get the application ID
        Optional<ApplicationInfo> applicationInfoOptional = MonetizationTest.gatewayDiscordClient.getApplicationInfo().blockOptional();

        Assertions.assertTrue(applicationInfoOptional.isPresent());

        MonetizationTest.applicationId = applicationInfoOptional.get().getId().asLong();

        // Get the monetization service
        MonetizationTest.monetizationService = MonetizationTest.gatewayDiscordClient.getRestClient().getMonetizationService();
    }

    @Order(1)
    @Nested
    @DisplayName("Test via the Rest client")
    class TestViaRest {

        @Order(1)
        @Nested
        @DisplayName("Test SKUs")
        class TestSKUs {

            @Test
            @DisplayName("List SKUs")
            void testListSKUs() {
                Flux<SkuData> skuFlux = MonetizationTest.monetizationService
                        .getAllSkus(MonetizationTest.applicationId)
                        .doOnNext(skuData -> {
                            if (skuData.type() == 5) {
                                MonetizationTest.skuId = skuData.id().asLong();
                            }
                        });

                AtomicBoolean seenInternalSKU = new AtomicBoolean(false);

                // We expect to see 2 SKUs with the same name, one with type 5 and one with type 6
                Predicate<SkuData> predicate = skuData -> skuData.name().equals(MonetizationTest.SKU_NAME)
                        && ((skuData.type() == 5 && seenInternalSKU.get()) || (skuData.type() == 6 && seenInternalSKU.compareAndSet(false, true)));

                StepVerifier.create(skuFlux)
                        .expectNextCount(2)
                        .expectNextMatches(predicate)
                        .expectNextMatches(predicate)
                        .expectComplete()
                        .verify();
            }

        }

        @Order(2)
        @Nested
        @DisplayName("Test Entitlements")
        class TestEntitlements {

            @Test
            @Order(1)
            @DisplayName("List Entitlements")
            void testListEntitlements() {
                Flux<EntitlementData> entitlementFlux = MonetizationTest.monetizationService
                        .getAllEntitlements(MonetizationTest.applicationId)
                        // Don't need the data, we just want to verify that the request is successful
                        .filter(entitlementData -> false);

                StepVerifier.create(entitlementFlux)
                        .expectComplete()
                        .verify();
            }

            @Test
            @Order(2)
            @DisplayName("Create test entitlement")
            void testCreateEntitlement() {
                AtomicReference<EntitlementData> entitlementReference = new AtomicReference<>();

                Mono<EntitlementData> createEntitlementMono = MonetizationTest.monetizationService
                        .createTestEntitlement(
                                MonetizationTest.applicationId,
                                CreateTestEntitlementRequest.builder()
                                        .ownerId(MonetizationTest.TEST_USER_ID)
                                        .ownerType(2)
                                        .skuId(MonetizationTest.skuId)
                                        .build()
                        ).doOnNext(entitlementReference::set);

                StepVerifier.create(createEntitlementMono)
                        .expectNextMatches(entitlementData -> {
                            Assertions.assertFalse(entitlementData.userId().isAbsent());
                            Assertions.assertEquals(MonetizationTest.TEST_USER_ID, entitlementData.userId().get().asLong());
                            Assertions.assertEquals(MonetizationTest.skuId, entitlementData.skuId().asLong());

                            return true;
                        })
                        .expectComplete()
                        .verify();

                Flux<EntitlementData> entitlementFlux = MonetizationTest.monetizationService
                        .getAllEntitlements(MonetizationTest.applicationId)
                        .filter(entitlementData -> entitlementData.id() == entitlementReference.get().id());

                StepVerifier.create(entitlementFlux)
                        .expectNext(entitlementReference.get())
                        .expectComplete()
                        .verify();

                MonetizationTest.testEntitlementId = entitlementReference.get().id().asLong();
            }

            @Test
            @Order(3)
            @DisplayName("Delete test entitlement")
            void testDeleteEntitlement() {
                Mono<Void> deleteEntitlementMono = MonetizationTest.monetizationService
                        .deleteTestEntitlement(MonetizationTest.applicationId, MonetizationTest.testEntitlementId);

                StepVerifier.create(deleteEntitlementMono)
                        .expectComplete()
                        .verify();

                Flux<EntitlementData> entitlementFlux = MonetizationTest.monetizationService
                        .getAllEntitlements(MonetizationTest.applicationId)
                        .filter(entitlementData -> entitlementData.id().asLong() == MonetizationTest.testEntitlementId);

                StepVerifier.create(entitlementFlux)
                        .expectNextCount(0)
                        .expectComplete()
                        .verify();
            }

        }
    }

    @Order(2)
    @Nested
    class TestViaGateway {

        @Test
        @Order(1)
        @DisplayName("Test ENTITLEMENT_CREATE event")
        void testEntitlementCreateEvent() {
            Mono<EntitlementData> createEntitlementMono = MonetizationTest.monetizationService
                    .createTestEntitlement(
                            MonetizationTest.applicationId,
                            CreateTestEntitlementRequest.builder()
                                    .ownerId(MonetizationTest.TEST_USER_ID)
                                    .ownerType(2)
                                    .skuId(MonetizationTest.skuId)
                                    .build()
                    ).doOnNext(entitlementData -> {
                        MonetizationTest.testEntitlementId = entitlementData.id().asLong();
                    });

            Flux<EntitlementCreateEvent> eventFlux = MonetizationTest.gatewayDiscordClient
                    .on(EntitlementCreateEvent.class)
                    .timeout(Duration.ofSeconds(5))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSubscribe(subscription -> {
                        // Create a test entitlement to trigger the event
                        createEntitlementMono.block();
                    });

            StepVerifier.create(eventFlux)
                    .expectNextCount(1)
                    .expectNextMatches(event -> {
                        Entitlement entitlement = event.getEntitlement();

                        return entitlement.getUserId().isPresent()
                                && entitlement.getUserId().get().asLong() == MonetizationTest.TEST_USER_ID;
                    })
                    .verifyTimeout(Duration.ofSeconds(5));
        }

        // This will be hard to test...
        @Test
        @Order(2)
        @DisplayName("Test ENTITLEMENT_UPDATE event")
        void testEntitlementUpdateEvent() {
            List<EntitlementData> entitlements = MonetizationTest.monetizationService
                    .getAllEntitlements(MonetizationTest.applicationId)
                    .collectList()
                    .block();

            Assertions.assertNotNull(entitlements);

            Flux<EntitlementUpdateEvent> eventFlux = MonetizationTest.gatewayDiscordClient
                    .on(EntitlementUpdateEvent.class)
                    .timeout(Duration.ofMinutes(5));

            StepVerifier.create(eventFlux)
                    .expectNextCount(1)
                    .expectNextMatches(event -> {
                        Entitlement newEntitlement = event.getEntitlement();

                        for (EntitlementData entitlementData : entitlements) {
                            Instant oldEndInstant = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(entitlementData.endsAt().get(), Instant::from);

                            if (entitlementData.id().asLong() == newEntitlement.getId().asLong()) {
                                return newEntitlement.getEndsAt().isPresent()
                                        && newEntitlement.getEndsAt().get().isAfter(oldEndInstant);
                            }
                        }

                        return false;
                    })
                    .verifyTimeout(Duration.ofMinutes(5));
        }

        @Test
        @Order(3)
        @DisplayName("Test premium required interaction")
        void testPremiumRequiredInteraction() {
            MonetizationTest.gatewayDiscordClient.getChannelById(Snowflake.of(MonetizationTest.TEST_TEXT_CHANNEL_ID))
                    .ofType(TextChannel.class)
                    .flatMap(textChannel -> {
                        return textChannel.createMessage("Test")
                                .withComponents(ActionRow.of(
                                        Button.primary("test-premium-required", "Test Premium Required")
                                ));
                    })
                    .block();

            Flux<ButtonInteractionEvent> eventFlux = MonetizationTest.gatewayDiscordClient
                    .on(ButtonInteractionEvent.class)
                    .filter(event -> event.getCustomId().equals("test-premium-required"))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnNext(event -> {
                        if (event.getInteraction().hasUserEntitlement()) {
                            event.reply("You have the required entitlement!").withEphemeral(true).subscribe();
                        } else {
                            event.displayPremiumRequired().subscribe();
                        }
                    })
                    .timeout(Duration.ofMinutes(5));

            StepVerifier.create(eventFlux)
                    .expectNextCount(2)
                    .expectNextMatches(event -> event.getInteraction().hasUserEntitlement())
                    .expectNextMatches(event -> !event.getInteraction().hasUserEntitlement())
                    .verifyTimeout(Duration.ofMinutes(5));
        }

        @Test
        @Order(4)
        @DisplayName("Test ENTITLEMENT_DELETE event")
        void testEntitlementDeleteEvent() {
            Mono<Void> deleteEntitlementMono = MonetizationTest.monetizationService
                    .deleteTestEntitlement(MonetizationTest.applicationId, MonetizationTest.testEntitlementId);

            Flux<EntitlementDeleteEvent> eventFlux = MonetizationTest.gatewayDiscordClient
                    .on(EntitlementDeleteEvent.class)
                    .timeout(Duration.ofSeconds(5))
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSubscribe(subscription -> {
                        // Delete the test entitlement to trigger the event
                        deleteEntitlementMono.block();
                    });

            StepVerifier.create(eventFlux)
                    .expectNextCount(1)
                    .expectNextMatches(event -> {
                        Entitlement entitlement = event.getEntitlement();

                        return entitlement.getUserId().isPresent()
                                && entitlement.getUserId().get().asLong() == MonetizationTest.TEST_USER_ID;
                    })
                    .verifyTimeout(Duration.ofSeconds(5));
        }

    }

    @AfterAll
    static void cleanup() {
        MonetizationTest.gatewayDiscordClient.logout().block();
    }
}
