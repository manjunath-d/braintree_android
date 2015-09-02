package com.braintreepayments.api.threedsecure;

import android.app.Activity;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.BraintreeFragmentTestUtils;
import com.braintreepayments.api.ThreeDSecure;
import com.braintreepayments.api.exceptions.AuthorizationException;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.PaymentMethodCreatedListener;
import com.braintreepayments.api.models.Card;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.PaymentMethod;
import com.braintreepayments.api.test.TestActivity;
import com.braintreepayments.testutils.TestClientTokenBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.pressBack;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static com.braintreepayments.testutils.ActivityResultHelper.getActivityResult;
import static com.braintreepayments.testutils.TestClientKey.CLIENT_KEY;
import static com.braintreepayments.testutils.ui.Matchers.withId;
import static com.braintreepayments.testutils.ui.ViewHelper.waitForView;
import static com.braintreepayments.testutils.ui.WaitForActivityHelper.waitForActivityToFinish;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ThreeDSecureVerificationTest {

    private static final String TEST_AMOUNT = "1";

    @Rule
    public final ActivityTestRule<TestActivity> mActivityTestRule =
            new ActivityTestRule<>(TestActivity.class);

    private Activity mActivity;
    private CountDownLatch mCountDownLatch;

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
        mCountDownLatch = new CountDownLatch(1);
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsWithStatusResultCanceledWhenUpIsPressed() {
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000002")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(getFragment(), cardBuilder, TEST_AMOUNT);

        waitForView(withId(android.R.id.widget_frame));
        onView(withContentDescription("Navigate up")).perform(click());

        waitForActivityToFinish(mActivity);
        Map<String, Object> result = getActivityResult(mActivity);

        assertEquals(Activity.RESULT_CANCELED, result.get("resultCode"));
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsWithStatusResultCanceledWhenBackIsPressedOnFirstPage() {
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000002")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(getFragment(), cardBuilder, TEST_AMOUNT);

        waitForView(withId(android.R.id.widget_frame));

        // wait for page to load
        SystemClock.sleep(5000);

        pressBack();

        waitForActivityToFinish(mActivity);
        Map<String, Object> result = getActivityResult(mActivity);

        assertEquals(Activity.RESULT_CANCELED, result.get("resultCode"));
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsWithStatusResultCanceledWhenUserGoesOnePageDeepAndPressesBackTwice() {
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000002")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(getFragment(), cardBuilder, TEST_AMOUNT);

        waitForView(withId(android.R.id.widget_frame));

        // wait for page to load and click a link
        SystemClock.sleep(10000);

        pressBack();
        SystemClock.sleep(2000);
        pressBack();

        waitForActivityToFinish(mActivity);
        Map<String, Object> result = getActivityResult(mActivity);

        assertEquals(Activity.RESULT_CANCELED, result.get("resultCode"));
    }

    @Test(timeout = 10000)
    @MediumTest
    public void performVerification_doesALookupAndReturnsACardAndANullACSUrlWhenAuthenticationIsNotRequired()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                Card card = (Card) paymentMethod;

                assertEquals("51", card.getLastTwo());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShifted());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShiftPossible());

                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000051")
                .expirationDate("12/20");

        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        mCountDownLatch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void performVerification_failsWithAClientKey() throws InterruptedException {
        BraintreeFragment fragment = BraintreeFragmentTestUtils.getFragment(mActivity, CLIENT_KEY);
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {
                assertTrue(throwable instanceof AuthorizationException);
                assertEquals("Client key authorization not allowed for this endpoint. Please use an authentication method with upgraded permissions", throwable.getMessage());
                mCountDownLatch.countDown();
            }

            @Override
            public void onRecoverableError(ErrorWithResponse error) {}
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000051")
                .expirationDate("12/20");

        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        mCountDownLatch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void performVerification_doesALookupAndReturnsACardWhenThereIsALookupError()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                assertEquals("77", ((Card) paymentMethod).getLastTwo());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000077")
                .expirationDate("12/20");

        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        mCountDownLatch.await();
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_requestsAuthenticationWhenRequired()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                Card card = (Card) paymentMethod;

                assertEquals("02", card.getLastTwo());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShifted());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShiftPossible());

                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000002")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        // Enter password and click submit
        SystemClock.sleep(10000);

        mCountDownLatch.await();
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsAnErrorWhenAuthenticationFails()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {}

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
                assertEquals("Failed to authenticate, please try a different form of payment",
                        error.getMessage());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000028")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        // Enter password and click submit, click continue on following page
        SystemClock.sleep(20000);

        mCountDownLatch.await();
    }

    @Test(timeout = 30000)
    @LargeTest
    public void performVerification_returnsASuccessfulAuthenticationWhenIssuerDoesNotParticipate()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                Card card = (Card) paymentMethod;

                assertEquals("01", card.getLastTwo());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShifted());
                assertTrue(card.getThreeDSecureInfo().isLiabilityShiftPossible());

                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000101")
                .expirationDate("12/30");

        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        mCountDownLatch.await();
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsAFailedAuthenticationWhenSignatureVerificationFails()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {}

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
                assertEquals("Failed to authenticate, please try a different form of payment",
                        error.getMessage());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000010")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        // Enter password and click submit
        SystemClock.sleep(10000);

        mCountDownLatch.await();
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsAnUnexpectedErrorWhenIssuerIsDown() throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {}

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
                assertEquals("An unexpected error occurred", error.getMessage());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000036")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        // Click continue
        SystemClock.sleep(10000);

        mCountDownLatch.await();
    }

//    @Test(timeout = 30000)
//    @LargeTest
    public void performVerification_returnsAnErrorWhenCardinalReturnsError()
            throws InterruptedException {
        BraintreeFragment fragment = getFragment();
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {}

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
                assertEquals("An unexpected error occurred", error.getMessage());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000093")
                .expirationDate("12/30");
        ThreeDSecure.performVerification(fragment, cardBuilder, TEST_AMOUNT);

        // Enter password and click submit
        SystemClock.sleep(10000);

        mCountDownLatch.await();
    }

    /* helpers */
    private BraintreeFragment getFragment() {
        String clientToken = new TestClientTokenBuilder().withThreeDSecure().build();
        return BraintreeFragmentTestUtils.getFragment(mActivity, clientToken);
    }
}
