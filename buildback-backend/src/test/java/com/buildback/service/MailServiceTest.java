package com.buildback.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Emails to reserved test domains are never sent for real (they would bounce back to the sender). */
class MailServiceTest {

    @Test
    void reservedTestDomainsAreRecognised() {
        assertThat(MailService.isReservedTestAddress("sabarmatiinfra@demo.buildback.test")).isTrue();
        assertThat(MailService.isReservedTestAddress("admin@buildback.local")).isTrue();
        assertThat(MailService.isReservedTestAddress("someone@shop.example")).isTrue();
        assertThat(MailService.isReservedTestAddress("x@nowhere.INVALID")).isTrue();

        assertThat(MailService.isReservedTestAddress("buyer@gmail.com")).isFalse();
        assertThat(MailService.isReservedTestAddress("seller@company.co.in")).isFalse();
        assertThat(MailService.isReservedTestAddress("person@testing.com")).isFalse(); // ".com", not ".test"
    }
}
