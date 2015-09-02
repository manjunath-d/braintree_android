package com.braintreepayments.api.models;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import com.braintreepayments.testutils.FixturesHelper;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class CardTest {

    @Test(timeout = 1000)
    @SmallTest
    public void canCreateCardFromJson() throws JSONException {
        String cardString = FixturesHelper.stringFromFixture(getTargetContext(),
                "payment_methods/visa_credit_card_response.json");

        Card card = Card.fromJson(cardString);

        assertEquals("Visa", card.getTypeLabel());
        assertEquals("123456-12345-12345-a-adfa", card.getNonce());
        assertEquals("ending in ••11", card.getDescription());
        assertEquals("11", card.getLastTwo());
    }
}