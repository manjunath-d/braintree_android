package com.braintreepayments.api.test;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.espresso.FailureHandler;
import android.support.test.espresso.base.DefaultFailureHandler;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.View;

import com.braintreepayments.api.BraintreePaymentActivity;
import com.braintreepayments.api.BraintreePaymentTestActivity;
import com.braintreepayments.api.PayPalTestSignatureVerification;
import com.braintreepayments.api.internal.BraintreeHttpClient;
import com.braintreepayments.api.internal.SignatureVerificationTestUtils;
import com.braintreepayments.testutils.ui.ViewHelper;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static android.support.test.espresso.Espresso.setFailureHandler;

@RunWith(AndroidJUnit4.class)
public class BraintreePaymentActivityTestRunner implements FailureHandler {

    @Rule
    public TestName mTestName = new TestName();

    @Rule
    public ActivityTestRule<BraintreePaymentTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BraintreePaymentTestActivity.class, true, false);

    private FailureHandler mDelegate;

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() {
        BraintreeHttpClient.DEBUG = true;
        PayPalTestSignatureVerification.disableAppSwitchSignatureVerification();
        SignatureVerificationTestUtils.disableSignatureVerification();

        mDelegate = new DefaultFailureHandler(getTargetContext());
        setFailureHandler(this);

        ((KeyguardManager) getTargetContext().getSystemService(Context.KEYGUARD_SERVICE))
                .newKeyguardLock("BraintreePaymentActivity")
                .disableKeyguard();
    }

    public BraintreePaymentActivity getActivity(String clientToken) {
        Intent intent = new Intent(getTargetContext(), BraintreePaymentTestActivity.class)
                .putExtra(BraintreePaymentActivity.EXTRA_CLIENT_TOKEN, clientToken);
        return mActivityTestRule.launchActivity(intent);
    }

    public BraintreePaymentActivity getActivity(String clientToken, long delay) {
        Intent intent = new Intent(getTargetContext(), BraintreePaymentTestActivity.class)
                .putExtra(BraintreePaymentActivity.EXTRA_CLIENT_TOKEN, clientToken)
                .putExtra(BraintreePaymentTestActivity.EXTRA_DELAY, delay);
        return mActivityTestRule.launchActivity(intent);
    }

    public BraintreePaymentActivity getActivity(String clientToken, Intent intent) {
        intent.setClass(getTargetContext(), BraintreePaymentTestActivity.class)
                .putExtra(BraintreePaymentActivity.EXTRA_CLIENT_TOKEN, clientToken);
        return mActivityTestRule.launchActivity(intent);
    }

    @Override
    public void handle(Throwable throwable, Matcher<View> matcher) {
        if (!ViewHelper.sWaitingForView) {
            Log.d("request_screenshot", mTestName.getMethodName() + "-" + System.currentTimeMillis());
            SystemClock.sleep(500);
        } else {
            SystemClock.sleep(20);
        }
        mDelegate.handle(throwable, matcher);
    }

    @Test(timeout = 100)
    public void test(){}
}
