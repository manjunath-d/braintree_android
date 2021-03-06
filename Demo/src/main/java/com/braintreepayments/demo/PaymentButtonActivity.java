package com.braintreepayments.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Window;

import com.braintreepayments.api.Braintree;
import com.braintreepayments.api.Braintree.BraintreeSetupFinishedListener;
import com.braintreepayments.api.Braintree.PaymentMethodCreatedListener;
import com.braintreepayments.api.dropin.BraintreePaymentActivity;
import com.braintreepayments.api.dropin.view.PaymentButton;
import com.braintreepayments.api.models.PaymentMethod;
import com.google.android.gms.wallet.Cart;
import com.paypal.android.sdk.payments.PayPalOAuthScopes;

import java.util.Collections;

public class PaymentButtonActivity extends Activity implements PaymentMethodCreatedListener,
        BraintreeSetupFinishedListener {

    private PaymentButton mPaymentButton;

    protected void onCreate(Bundle onSaveInstanceState) {
        super.onCreate(onSaveInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.payment_button);

        mPaymentButton = (PaymentButton) findViewById(R.id.payment_button);

        Braintree.setup(this, getIntent().getStringExtra(BraintreePaymentActivity.EXTRA_CLIENT_TOKEN),
                this);
    }

    @Override
    public void onBraintreeSetupFinished(boolean setupSuccessful, Braintree braintree,
            String errorMessage, Exception exception) {
        if (setupSuccessful) {
            braintree.addListener(this);

            Cart cart = getIntent().getParcelableExtra(BraintreePaymentActivity.EXTRA_ANDROID_PAY_CART);
            boolean isBillingAgreement = getIntent().getBooleanExtra(BraintreePaymentActivity.EXTRA_ANDROID_PAY_IS_BILLING_AGREEMENT, false);
            boolean shippingAddressRequired = getIntent().getBooleanExtra("shippingAddressRequired", false);
            boolean phoneNumberRequired = getIntent().getBooleanExtra("phoneNumberRequired", false);
            mPaymentButton.setAndroidPayOptions(cart, isBillingAgreement, shippingAddressRequired,
                    phoneNumberRequired);
            boolean payPalAddressScopeRequested = getIntent().getBooleanExtra("payPalAddressScopeRequested", false);
            if (payPalAddressScopeRequested) {
                mPaymentButton.setAdditionalPayPalScopes(
                        Collections.singletonList(PayPalOAuthScopes.PAYPAL_SCOPE_ADDRESS));
            }
            mPaymentButton.initialize(this, braintree);
        } else {
            Intent intent = new Intent()
                    .putExtra(BraintreePaymentActivity.EXTRA_ERROR_MESSAGE, errorMessage);
            setResult(RESULT_FIRST_USER, intent);
            finish();
        }
    }

    @Override
    public void onPaymentMethodCreated(PaymentMethod paymentMethod) {
        setResult(RESULT_OK, new Intent()
                .putExtra(BraintreePaymentActivity.EXTRA_PAYMENT_METHOD, (Parcelable) paymentMethod));
        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int responseCode, Intent data) {
        if (responseCode == RESULT_OK && requestCode == PaymentButton.REQUEST_CODE) {
            setProgressBarIndeterminateVisibility(true);
            mPaymentButton.onActivityResult(requestCode, responseCode, data);
        } else {
            setProgressBarIndeterminateVisibility(false);
        }
    }
}
