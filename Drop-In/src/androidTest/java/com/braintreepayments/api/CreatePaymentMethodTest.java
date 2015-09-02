package com.braintreepayments.api;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Parcelable;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.KeyEvent;
import android.widget.Button;

import com.braintreepayments.api.dropin.view.LoadingHeader;
import com.braintreepayments.api.dropin.view.LoadingHeader.HeaderState;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.models.Card;
import com.braintreepayments.api.models.PaymentMethod;
import com.braintreepayments.api.test.BraintreePaymentActivityTestRunner;
import com.braintreepayments.cardform.view.ErrorEditText;
import com.braintreepayments.testutils.TestClientTokenBuilder;

import org.junit.Test;

import java.util.Map;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isEnabled;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.braintreepayments.api.utils.Assertions.assertSelectedPaymentMethodIs;
import static com.braintreepayments.api.utils.PaymentFormHelpers.addCardAndAssertSuccess;
import static com.braintreepayments.api.utils.PaymentFormHelpers.fillInCardForm;
import static com.braintreepayments.api.utils.PaymentFormHelpers.fillInOfflinePayPal;
import static com.braintreepayments.api.utils.PaymentFormHelpers.onAddPaymentFormHeader;
import static com.braintreepayments.api.utils.PaymentFormHelpers.performPayPalAdd;
import static com.braintreepayments.api.utils.PaymentFormHelpers.waitForAddPaymentFormHeader;
import static com.braintreepayments.api.utils.PaymentFormHelpers.waitForPaymentMethodList;
import static com.braintreepayments.testutils.ActivityResultHelper.getActivityResult;
import static com.braintreepayments.testutils.CardNumber.VISA;
import static com.braintreepayments.testutils.FixturesHelper.stringFromFixture;
import static com.braintreepayments.testutils.TestClientKey.CLIENT_KEY;
import static com.braintreepayments.testutils.ui.Matchers.withHint;
import static com.braintreepayments.testutils.ui.Matchers.withId;
import static com.braintreepayments.testutils.ui.ViewHelper.FIFTEEN_SECONDS;
import static com.braintreepayments.testutils.ui.ViewHelper.TEN_SECONDS;
import static com.braintreepayments.testutils.ui.ViewHelper.THREE_SECONDS;
import static com.braintreepayments.testutils.ui.ViewHelper.TWO_SECONDS;
import static com.braintreepayments.testutils.ui.ViewHelper.closeSoftKeyboard;
import static com.braintreepayments.testutils.ui.ViewHelper.waitForView;
import static com.braintreepayments.testutils.ui.WaitForActivityHelper.waitForActivityToFinish;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.not;

@LargeTest
public class CreatePaymentMethodTest extends BraintreePaymentActivityTestRunner {

    @Test(timeout = 30000)
    public void cardFormCreatesAPaymentMethodWithoutACustomer() {
        addCardAndAssertSuccess(getActivity(new TestClientTokenBuilder().withoutCustomer().build()));
    }

    @Test(timeout = 30000)
    public void cardFormCreatesAPaymentMethodWithACustomer() {
        addCardAndAssertSuccess(getActivity(new TestClientTokenBuilder().build()));
    }

    @Test(timeout = 30000)
    public void cardFormCreatesAPaymentMethodWithAClientKey() {
        addCardAndAssertSuccess(getActivity(CLIENT_KEY));
    }

    @Test(timeout = 30000)
    public void cardFormCreatesAPaymentMethodWithoutCvvOrPostalCode() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder()
                .withoutCvvChallenge()
                .withoutPostalCodeChallenge()
                .build());

        waitForAddPaymentFormHeader();
        onView(withHint("Card Number")).perform(typeText(VISA));
        onView(withHint("Expiration")).perform(typeText("0619"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_cvv)).check(matches(not(isDisplayed())));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_postal_code)).check(matches(not(isDisplayed())));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        waitForActivityToFinish(activity);

        Map<String, Object> result = getActivityResult(activity);
        PaymentMethod response = ((Intent) result.get("resultData"))
                        .getParcelableExtra(BraintreePaymentActivity.EXTRA_PAYMENT_METHOD);

        assertEquals(Activity.RESULT_OK, result.get("resultCode"));
        assertNotNull(response.getNonce());
        assertEquals("11", ((Card) response).getLastTwo());
    }

    @Test(timeout = 30000)
    public void paypalCreatesAPaymentMethodWithACustomer() {
        String clientToken = new TestClientTokenBuilder().withPayPal().build();

        assertCreatePaymentMethodFromPayPal(clientToken, "jane.doe@example.com");
    }

    @Test(timeout = 30000)
    public void paypalCreatesAPaymentMethodWithoutACustomer() {
        String clientToken = new TestClientTokenBuilder().withoutCustomer().withPayPal().build();

        assertCreatePaymentMethodFromPayPal(clientToken, "jane.doe@example.com");
    }

    @Test(timeout = 30000)
    public void returnsToSelectPaymentMethodViewAfterAddingAPayPalAccount() {
        String clientToken = new TestClientTokenBuilder().withFakePayPal().build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.MOCK_CONFIGURATION, clientToken);
        getActivity(clientToken, intent);

        fillInOfflinePayPal();

        waitForPaymentMethodList(TEN_SECONDS);
        assertSelectedPaymentMethodIs(com.braintreepayments.api.dropin.R.string.bt_descriptor_paypal);
    }

    @Test(timeout = 30000)
    public void displaysLoadingViewWhileCreatingAPayPalAccount() {
        String clientToken = new TestClientTokenBuilder().withFakePayPal().build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.MOCK_CONFIGURATION, clientToken)
                .putExtra(BraintreePaymentTestActivity.EXTRA_DELAY, THREE_SECONDS);
        getActivity(clientToken, intent);

        fillInOfflinePayPal();

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_inflated_loading_view)).check(matches(isDisplayed()));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_header)).check(matches(not(isDisplayed())));
        waitForPaymentMethodList().check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void disablesSubmitButtonWhileCreatingPaymentMethod() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().build(), 200);

        fillInCardForm();
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        Button submitButton = (Button) activity.findViewById(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button);
        assertFalse(submitButton.isEnabled());

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_card_number)).perform(click());
        assertFalse(submitButton.isEnabled());

        waitForActivityToFinish(activity);

        Map<String, Object> result = getActivityResult(activity);
        PaymentMethod response = ((Intent) result.get("resultData"))
                .getParcelableExtra(BraintreePaymentActivity.EXTRA_PAYMENT_METHOD);

        assertEquals(Activity.RESULT_OK, result.get("resultCode"));
        assertNotNull(response.getNonce());
        assertEquals("11", ((Card) response).getLastTwo());
    }

    @Test(timeout = 30000)
    public void reEnablesSubmitButtonIfThereAreValidationErrorsForThePaymentMethod() {
        String clientToken = new TestClientTokenBuilder()
                .withoutPostalCodeChallenge()
                .withCvvVerification()
                .build();
        getActivity(clientToken, THREE_SECONDS);

        waitForAddPaymentFormHeader();

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_card_number)).perform(typeText(VISA));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_expiration)).perform(typeText("1219"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_cvv)).perform(typeText("200"));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).check(matches(not(isEnabled())));
        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button), isEnabled());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).check(matches(isEnabled()));
    }

    @Test(timeout = 30000)
    public void finishesActivityWithErrorIfANonCreditCardErrorIsReturned() {
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.TOKENIZE_CREDIT_CARD_ERROR,
                        (Parcelable) new ErrorWithResponse(422, "{}"));
        Activity activity = getActivity(new TestClientTokenBuilder().build(), intent);

        waitForAddPaymentFormHeader();
        onView(withHint(com.braintreepayments.api.dropin.R.string.bt_form_hint_card_number))
                .perform(typeText(VISA), closeSoftKeyboard());
        onView(withHint(com.braintreepayments.api.dropin.R.string.bt_form_hint_expiration))
                .perform(typeText("0619"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        waitForActivityToFinish(activity);
        Map<String, Object> result = getActivityResult(activity);
        assertEquals(BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_ERROR,
                result.get("resultCode"));
        Throwable error = (Throwable) ((Intent) result.get("resultData"))
                .getSerializableExtra(BraintreePaymentActivity.EXTRA_ERROR_MESSAGE);
        assertEquals("Parsing error response failed", error.getMessage());
    }

    @Test(timeout = 30000)
    public void paypalButtonIsNotShownIfPayPalIsNotSupported() {
        getActivity(new TestClientTokenBuilder().build());

        waitForAddPaymentFormHeader();

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_paypal_button)).check(matches(not(isDisplayed())));
    }

    @Test(timeout = 30000)
    public void backButtonExitsTheActivityIfThereAreNoPaymentMethodsToSelectFrom() {
        Activity activity = getActivity(new TestClientTokenBuilder().build());
        waitForAddPaymentFormHeader();
        onAddPaymentFormHeader().check(matches(isDisplayed()));
        assertFalse(activity.isFinishing());

        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        assertTrue(activity.isFinishing());
    }

    @Test(timeout = 30000)
    public void backButtonInPayPalTakesYouBackToAddPaymentMethodView() {
        String clientToken = new TestClientTokenBuilder().withFakePayPal().build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.MOCK_CONFIGURATION, clientToken);
        getActivity(clientToken, intent);

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_paypal_button)).perform(click());
        waitForView(withHint("Email")).check(matches(isDisplayed())).perform(closeSoftKeyboard());
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        waitForAddPaymentFormHeader().check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void backButtonDuringCreditCardAddDoesNothing() {
        getActivity(new TestClientTokenBuilder().build(), TWO_SECONDS);
        fillInCardForm();
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(
                click());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(
                matches(isDisplayed()));

        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void backButtonDuringPayPalAddDoesNothing() {
        String clientToken = new TestClientTokenBuilder().withFakePayPal().build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.MOCK_CONFIGURATION, clientToken)
                .putExtra(BraintreePaymentTestActivity.EXTRA_DELAY, TWO_SECONDS);
        getActivity(clientToken, intent);
        fillInOfflinePayPal();
        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_inflated_loading_view)).check(
                matches(isDisplayed()));

        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_inflated_loading_view)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void upButtonIsNotShownIfThereAreNoPaymentMethods() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().build());

        waitForAddPaymentFormHeader();

        assertFalse("Expected up not to be present on action bar", checkHomeAsUpEnabled(activity));
    }

    @Test(timeout = 30000)
    public void upButtonIsShownAfterYouAddAPaymentMethod() {
        String clientToken = new TestClientTokenBuilder().withFakePayPal().build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.MOCK_CONFIGURATION, clientToken);
        BraintreePaymentActivity activity = getActivity(clientToken, intent);
        performPayPalAdd();

        assertFalse("Expected up not to be present on action bar", checkHomeAsUpEnabled(activity));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_change_payment_method_link)).perform(click());

        assertTrue("Expected up to be present on action bar", checkHomeAsUpEnabled(activity));
    }

    @Test(timeout = 30000)
    public void displaysAnErrorWhenCardNumberFailsOnServer() {
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.TOKENIZE_CREDIT_CARD_ERROR,
                        (Parcelable) new ErrorWithResponse(422, stringFromFixture("errors/card_number_error_response.json")));
        Activity activity = getActivity(new TestClientTokenBuilder().build(), intent);

        waitForAddPaymentFormHeader();
        onView(withHint(com.braintreepayments.api.dropin.R.string.bt_form_hint_card_number))
                .perform(typeText(VISA), closeSoftKeyboard());
        onView(withHint(com.braintreepayments.api.dropin.R.string.bt_form_hint_expiration))
                .perform(typeText("0619"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button), isEnabled());

        ErrorEditText editText = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_card_number);
        assertTrue(editText.isError());
        assertTrue(editText.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void displaysAnErrorWhenExpirationFailsOnServer() {
        String clientToken = new TestClientTokenBuilder()
                .withoutCvvChallenge()
                .withoutPostalCodeChallenge()
                .build();
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.TOKENIZE_CREDIT_CARD_ERROR,
                       (Parcelable) new ErrorWithResponse(422, stringFromFixture("errors/expiration_date_error_response.json")));
        Activity activity = getActivity(clientToken, intent);

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_card_number)).perform(typeText(VISA));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_expiration)).perform(typeText("0815"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button), isEnabled());

        ErrorEditText editText = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_expiration);
        assertTrue(editText.isError());
        assertTrue(editText.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void displaysAnErrorWhenPostalCodeFailsOnServer() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().withPostalCodeVerification().build());

        waitForView(
                withId(com.braintreepayments.api.dropin.R.id.bt_card_form_card_number)).perform(typeText(
                VISA));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_expiration)).perform(typeText("0815"), closeSoftKeyboard());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_postal_code)).perform(typeText("20000"));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button), isEnabled());

        ErrorEditText editText = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_postal_code);
        assertTrue(editText.isError());
        assertTrue(editText.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void returnsUnexpectedErrorWhenServerReturnsNonCreditCardError() {
        Intent intent = new Intent()
                .putExtra(BraintreePaymentTestActivity.TOKENIZE_CREDIT_CARD_ERROR,
                        (Parcelable) new ErrorWithResponse(422, stringFromFixture("responses/error_response.json")));
        Activity activity = getActivity(new TestClientTokenBuilder().build(), intent);

        fillInCardForm();

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());
        waitForActivityToFinish(activity);

        Map<String, Object> result = getActivityResult(activity);
        assertEquals(BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_ERROR,
                result.get("resultCode"));
    }

    @Test(timeout = 30000)
    public void errorIsShownWhenCvvDoesNotMatchForCvvVerificationMerchants() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().withCvvVerification().build());
        waitForAddPaymentFormHeader();

        onView(withHint("Card Number")).perform(typeText(VISA));
        onView(withHint("Expiration")).perform(typeText("0619"), closeSoftKeyboard());
        onView(withHint("CVV")) .perform(typeText("200"), closeSoftKeyboard());

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());
        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_header_message));

        ErrorEditText editText = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_cvv);
        assertTrue(editText.isError());
        assertTrue(editText.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void errorIsShownWhenPostalCodeDoesNotMatchForPostalCodeVerificationMerchants() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().withPostalCodeVerification().build());
        waitForAddPaymentFormHeader();

        onView(withHint("Card Number")).perform(typeText(VISA));
        onView(withHint("Expiration")).perform(typeText("0619"), closeSoftKeyboard());
        onView(withHint("Postal Code")).perform(typeText("20000"));

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());
        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_header_message));

        ErrorEditText editText = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_postal_code);
        assertTrue(editText.isError());
        assertTrue(editText.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void errorIsShownWhenCvvAndPostalCodeDoesNotMatchForCvvAndPostalCodeVerificationMerchants() {
        BraintreePaymentActivity activity = getActivity(new TestClientTokenBuilder().withCvvAndPostalCodeVerification().build());
        waitForAddPaymentFormHeader();

        onView(withHint("Card Number")).perform(typeText(VISA));
        onView(withHint("Expiration")).perform(typeText("0619"), closeSoftKeyboard());
        onView(withHint("CVV")) .perform(typeText("200"), closeSoftKeyboard());
        onView(withHint("Postal Code")).perform(typeText("20000"));

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_card_form_submit_button)).perform(click());
        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_header_message));

        ErrorEditText cvvCode = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_cvv);
        ErrorEditText postalCode = (ErrorEditText) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_card_form_postal_code);

        assertTrue(cvvCode.isError());
        assertTrue(postalCode.isError());
        assertTrue(cvvCode.isFocused());

        LoadingHeader loadingHeader = (LoadingHeader) activity.findViewById(
                com.braintreepayments.api.dropin.R.id.bt_header_container);
        assertEquals(HeaderState.ERROR, loadingHeader.getCurrentState());
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_header_container)).check(matches(isDisplayed()));
    }

    @Test(timeout = 30000)
    public void isSuccessfulWhenCvvAndPostalCodeMatchForCvvAndPostalCodeVerificationMerchants() {
        addCardAndAssertSuccess(getActivity(
                new TestClientTokenBuilder().withCvvAndPostalCodeVerification().build()));
    }

    /* helpers */
    private void assertCreatePaymentMethodFromPayPal(String clientToken, String descriptionEmail) {
        BraintreePaymentActivity activity = getActivity(clientToken);

        waitForView(withId(com.braintreepayments.api.dropin.R.id.bt_paypal_button)).perform(click());
        waitForView(withHint("Email"), FIFTEEN_SECONDS).perform(typeText("bt_buyer_us@paypal.com"));
        onView(withHint("Password")).perform(typeText("11111111"));
        onView(withHint("Log In")).perform(click());
        waitForPaymentMethodList();

        onView(withId(com.braintreepayments.api.dropin.R.id.bt_payment_method_description)).check(
                matches(withText(descriptionEmail)));
        onView(withId(com.braintreepayments.api.dropin.R.id.bt_select_payment_method_submit_button)).perform(click());

        waitForActivityToFinish(activity);

        Map<String, Object> result = getActivityResult(activity);
        PaymentMethod paymentMethod = ((Intent) result.get("resultData"))
                .getParcelableExtra(BraintreePaymentActivity.EXTRA_PAYMENT_METHOD);

        assertEquals(Activity.RESULT_OK, result.get("resultCode"));
        assertNotNull(paymentMethod.getNonce());
    }

    private boolean checkHomeAsUpEnabled(Activity activity) {
        return (activity.getActionBar().getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) ==
            ActionBar.DISPLAY_HOME_AS_UP;
    }
}
