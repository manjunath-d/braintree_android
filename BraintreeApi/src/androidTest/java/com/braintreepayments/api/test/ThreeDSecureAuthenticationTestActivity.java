package com.braintreepayments.api.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.braintreepayments.api.Braintree;
import com.braintreepayments.api.Braintree.BraintreeSetupFinishedListener;

public class ThreeDSecureAuthenticationTestActivity extends Activity {

    public static final String EXTRA_CLIENT_TOKEN = "client_token";
    public static final String EXTRA_NONCE = "nonce";
    public static final String EXTRA_AMOUNT = "amount";

    private static final int THREE_D_SECURE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Braintree.setup(this, getIntent().getStringExtra(EXTRA_CLIENT_TOKEN),
                new BraintreeSetupFinishedListener() {
                    @Override
                    public void onBraintreeSetupFinished(boolean setupSuccessful,
                            Braintree braintree, String errorMessage, Exception exception) {
                        String nonce = getIntent().getStringExtra(EXTRA_NONCE);
                        String amount = getIntent().getStringExtra(EXTRA_AMOUNT);

                        braintree.startThreeDSecureVerification(
                                ThreeDSecureAuthenticationTestActivity.this, THREE_D_SECURE_REQUEST,
                                nonce, amount);
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == THREE_D_SECURE_REQUEST) {
            setResult(resultCode, data);
            finish();
        }
    }
}
