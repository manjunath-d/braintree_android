package com.braintreepayments.api;

import android.app.Activity;
import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.PaymentMethodCreatedListener;
import com.braintreepayments.api.models.Card;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PaymentMethod;
import com.braintreepayments.api.models.ThreeDSecureAuthenticationResponse;
import com.braintreepayments.api.test.TestActivity;
import com.braintreepayments.api.threedsecure.ThreeDSecureWebViewActivity;
import com.braintreepayments.testutils.TestClientTokenBuilder;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.BraintreeFragmentTestUtils.getFragment;
import static com.braintreepayments.api.BraintreeFragmentTestUtils.getMockFragment;
import static com.braintreepayments.api.BraintreeFragmentTestUtils.tokenize;
import static com.braintreepayments.testutils.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(AndroidJUnit4.class)
public class ThreeDSecureTest {

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

    @Test(timeout = 10000)
    @MediumTest
    public void performVerification_postsPaymentMethodToListenersWhenLookupReturnsACard()
            throws InterruptedException, InvalidArgumentException {
        String clientToken = new TestClientTokenBuilder().withThreeDSecure().build();
        BraintreeFragment fragment = getFragment(mActivity, clientToken);
        String nonce = tokenize(fragment, new CardBuilder()
                .cardNumber("4000000000000051")
                .expirationDate("12/20")).getNonce();
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                assertEquals("51", ((Card) paymentMethod).getLastTwo());
                mCountDownLatch.countDown();
            }
        });

        ThreeDSecure.performVerification(fragment, nonce, "5");

        mCountDownLatch.await();
    }

    @Test(timeout = 10000)
    @MediumTest
    public void performVerification_acceptsACardBuilderAndPostsAPaymentMethodToListener()
            throws InterruptedException {
        String clientToken = new TestClientTokenBuilder().withThreeDSecure().build();
        BraintreeFragment fragment = getFragment(mActivity, clientToken);
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                assertEquals("51", ((Card) paymentMethod).getLastTwo());
                mCountDownLatch.countDown();
            }
        });
        CardBuilder cardBuilder = new CardBuilder()
                .cardNumber("4000000000000051")
                .expirationDate("12/20");

        ThreeDSecure.performVerification(fragment, cardBuilder, "5");

        mCountDownLatch.await();
    }

    @Test(timeout = 1000)
    @SmallTest
    public void onActivityResult_postsPaymentMethodToListener()
            throws JSONException, InterruptedException{
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        fragment.addListener(new PaymentMethodCreatedListener() {
            @Override
            public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
                assertEquals("11", ((Card) paymentMethod).getLastTwo());
                mCountDownLatch.countDown();
            }
        });
        JSONObject authResponse = new JSONObject(
                stringFromFixture("three_d_secure/authentication_response.json"));
        Intent data = new Intent()
                .putExtra(ThreeDSecureWebViewActivity.EXTRA_THREE_D_SECURE_RESULT,
                        ThreeDSecureAuthenticationResponse.fromJson(authResponse.toString()));

        ThreeDSecure.onActivityResult(fragment, Activity.RESULT_OK, data);

        mCountDownLatch.await();
    }

    @Test(timeout = 1000)
    @SmallTest
    public void onActivityResult_postsUnrecoverableErrorsToListeners() throws InterruptedException {
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {
                assertEquals("Error!", throwable.getMessage());
                mCountDownLatch.countDown();
            }

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
            }
        });
        ThreeDSecureAuthenticationResponse authResponse =
                ThreeDSecureAuthenticationResponse.fromException("Error!");
        Intent data = new Intent()
                .putExtra(ThreeDSecureWebViewActivity.EXTRA_THREE_D_SECURE_RESULT, authResponse);

        ThreeDSecure.onActivityResult(fragment, Activity.RESULT_OK, data);

        mCountDownLatch.await();
    }

    @Test(timeout = 1000)
    @SmallTest
    public void onActivityResult_postsRecoverableErrorsToListener() throws InterruptedException {
        BraintreeFragment fragment = getMockFragment(mActivity, mock(Configuration.class));
        fragment.addListener(new BraintreeErrorListener() {
            @Override
            public void onUnrecoverableError(Throwable throwable) {
            }

            @Override
            public void onRecoverableError(ErrorWithResponse error) {
                assertEquals("Failed to authenticate, please try a different form of payment",
                        error.getMessage());
                mCountDownLatch.countDown();
            }
        });
        Intent data = new Intent()
                .putExtra(ThreeDSecureWebViewActivity.EXTRA_THREE_D_SECURE_RESULT,
                        ThreeDSecureAuthenticationResponse.fromJson(
                                stringFromFixture("errors/three_d_secure_error.json")));

        ThreeDSecure.onActivityResult(fragment, Activity.RESULT_OK, data);

        mCountDownLatch.await();
    }

    @Test(timeout = 1000)
    @SmallTest
    public void onActivityResult_doesNothingWhenResultCodeNotOk() {
        Intent intent = mock(Intent.class);

        ThreeDSecure.onActivityResult(mock(BraintreeFragment.class), Activity.RESULT_CANCELED,
                intent);

        verifyZeroInteractions(intent);
    }
}
