package com.braintreepayments.api;

import android.app.Activity;
import android.content.Intent;
import android.support.test.filters.FlakyTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.test.TestActivity;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.BraintreeFragmentTestUtils.getMockFragment;
import static com.braintreepayments.testutils.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class PayPalTest {

    @Rule
    public final ActivityTestRule<TestActivity> mActivityTestRule =
            new ActivityTestRule<>(TestActivity.class);

    private Activity mActivity;
    private CountDownLatch mLatch;

    @Before
    public void setUp() {
        mActivity = mActivityTestRule.getActivity();
        mLatch = new CountDownLatch(1);
    }

    @Test(timeout = 1000)
    @SmallTest
    public void authorizeAccount_startsPayPal() throws JSONException, InterruptedException {
        fail("Not fixed");
        Configuration configuration = Configuration.fromJson(
                stringFromFixture("configuration_with_offline_paypal.json"));
        final ArgumentCaptor<Intent> launchIntentCaptor = ArgumentCaptor.forClass(Intent.class);
        final BraintreeFragment fragment = getMockFragment(mActivity, configuration);


        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                doAnswer(new Answer() {
                    @Override
                    public Object answer(InvocationOnMock invocation) throws Throwable {
                        verify(fragment).startActivityForResult(launchIntentCaptor.capture(), eq(PayPal.PAYPAL_AUTHORIZATION_REQUEST_CODE));
                        mLatch.countDown();
                        return null;
                    }
                }).when(fragment).startActivityForResult(any(Intent.class), eq(PayPal.PAYPAL_AUTHORIZATION_REQUEST_CODE));
                PayPal.authorizeAccount(fragment);
            }
        });
        mLatch.await();
    }


    @Test(timeout = 1000)
    @SmallTest
    @FlakyTest
    public void authorizeAccount_sendsAnalyticsEvent() throws JSONException, InterruptedException {
        Configuration configuration = Configuration.fromJson(
                stringFromFixture("configuration_with_offline_paypal.json"));
        final BraintreeFragment fragment = getMockFragment(mActivity, configuration);
        doNothing().when(fragment).startActivityForResult(any(Intent.class), anyInt());

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PayPal.authorizeAccount(fragment);
            }
        });

        verify(fragment).sendAnalyticsEvent("paypal.selected");
    }
}

