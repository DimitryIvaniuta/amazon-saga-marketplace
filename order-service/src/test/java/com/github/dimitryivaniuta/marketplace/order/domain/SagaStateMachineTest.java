package com.github.dimitryivaniuta.marketplace.order.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

/** Tests explicit Saga transition policy. */
class SagaStateMachineTest {
    /** Verifies the happy-path transition is accepted. */
    @Test
    void shouldAllowHappyPathTransition() {
        assertThatCode(() -> SagaStateMachine.requireAllowed(
                "WAITING_INVENTORY", "WAITING_PAYMENT_AUTHORIZATION")).doesNotThrowAnyException();
    }
    /** Verifies out-of-order events cannot skip payment authorization. */
    @Test
    void shouldRejectIllegalTransition() {
        assertThatThrownBy(() -> SagaStateMachine.requireAllowed(
                "WAITING_INVENTORY", "WAITING_PAYMENT_CAPTURE")).isInstanceOf(IllegalStateException.class);
    }
    /** A reservation expiry waits for an ambiguous authorization to resolve safely. */
    @Test
    void shouldAllowLateAuthorizationCompensationPath() {
        assertThatCode(() -> SagaStateMachine.requireAllowed(
                "WAITING_PAYMENT_AUTHORIZATION", "WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SagaStateMachine.requireAllowed(
                "WAITING_PAYMENT_RESOLUTION_AFTER_EXPIRY", "COMPENSATING_PAYMENT"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SagaStateMachine.requireAllowed(
                "COMPENSATING_PAYMENT", "CANCELLED"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SagaStateMachine.requireAllowed(
                "WAITING_INVENTORY_COMMIT", "COMPENSATING_PAYMENT"))
                .doesNotThrowAnyException();
    }

}
