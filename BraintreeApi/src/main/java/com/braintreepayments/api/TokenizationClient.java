package com.braintreepayments.api;

import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.interfaces.ConfigurationListener;
import com.braintreepayments.api.interfaces.HttpResponseCallback;
import com.braintreepayments.api.interfaces.PaymentMethodResponseCallback;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PayPalAccountBuilder;
import com.braintreepayments.api.models.PaymentMethod;
import com.braintreepayments.api.models.PaymentMethodBuilder;

import org.json.JSONException;

import java.util.List;

public class TokenizationClient {

    static final String PAYMENT_METHOD_ENDPOINT = "payment_methods";

    /**
     * Retrieves the current list of {@link PaymentMethod} for the current customer.
     * <p/>
     * When finished, the {@link java.util.List} of {@link PaymentMethod}s will be sent to {@link
     * com.braintreepayments.api.interfaces.PaymentMethodsUpdatedListener#onPaymentMethodsUpdated(List)}.
     *
     * @param fragment {@link BraintreeFragment}
     */
    static void getPaymentMethods(final BraintreeFragment fragment) {
        fragment.waitForConfiguration(new ConfigurationListener() {
            @Override
            public void onConfigurationFetched(Configuration configuration) {
                fragment.getHttpClient().get(versionedPath(PAYMENT_METHOD_ENDPOINT),
                        new HttpResponseCallback() {
                            @Override
                            public void success(String responseBody) {
                                try {
                                    List<PaymentMethod> paymentMethods =
                                            PaymentMethod.parsePaymentMethods(responseBody);

                                    fragment.postCallback(paymentMethods);
                                } catch (JSONException e) {
                                    fragment.postCallback(e);
                                }
                            }

                            @Override
                            public void failure(Exception exception) {
                                fragment.postCallback(exception);
                            }
                        });
            }
        });
    }

    /**
     * Create a {@link com.braintreepayments.api.models.PaymentMethod} in the Braintree Gateway.
     * <p/>
     * On completion, returns the {@link PaymentMethod} to {@link PaymentMethodResponseCallback}.
     * <p/>
     * If creation fails validation, {@link com.braintreepayments.api.interfaces.BraintreeErrorListener#onRecoverableError(ErrorWithResponse)}
     * will be called with the resulting {@link ErrorWithResponse}.
     * <p/>
     * If an error not due to validation (server error, network issue, etc.) occurs, {@link
     * com.braintreepayments.api.interfaces.BraintreeErrorListener#onUnrecoverableError(Throwable)}
     * will be called with the {@link Exception} that occurred.
     *
     * @param paymentMethodBuilder {@link PaymentMethodBuilder} for the {@link PaymentMethod} to be
     * created.
     */
    static void tokenize(final BraintreeFragment fragment,
            final PaymentMethodBuilder paymentMethodBuilder,
            final PaymentMethodResponseCallback callback) {
        fragment.waitForConfiguration(new ConfigurationListener() {
            @Override
            public void onConfigurationFetched(Configuration configuration) {
                fragment.getHttpClient().post(TokenizationClient.versionedPath(
                                TokenizationClient.PAYMENT_METHOD_ENDPOINT + "/" +
                                        paymentMethodBuilder.getApiPath()),
                        paymentMethodBuilder.build(), new HttpResponseCallback() {
                            @Override
                            public void success(String responseBody) {
                                try {
                                    PaymentMethod paymentMethod =
                                            PaymentMethod.parsePaymentMethod(responseBody,
                                                    paymentMethodBuilder
                                                            .getResponsePaymentMethodType());
                                    callback.success(paymentMethod);
                                } catch (JSONException e) {
                                    callback.failure(e);
                                }
                            }

                            @Override
                            public void failure(Exception exception) {
                                callback.failure(exception);
                            }
                        });
            }
        });
    }

    /**
     * Create a {@link com.braintreepayments.api.models.Card} in the Braintree Gateway.
     * <p/>
     * On completion, returns the {@link PaymentMethod} to {@link com.braintreepayments.api.interfaces.PaymentMethodCreatedListener}.
     * <p/>
     * If creation fails validation, {@link com.braintreepayments.api.interfaces.BraintreeErrorListener#onRecoverableError(ErrorWithResponse)}
     * will be called with the resulting {@link ErrorWithResponse}.
     * <p/>
     * If an error not due to validation (server error, network issue, etc.) occurs, {@link
     * com.braintreepayments.api.interfaces.BraintreeErrorListener#onUnrecoverableError(Throwable)}
     * will be called with the {@link Exception} that occurred.
     *
     * @param paymentMethodBuilder {@link PaymentMethodBuilder}
     */
    public static void tokenize(final BraintreeFragment fragment, final PaymentMethodBuilder paymentMethodBuilder) {
        TokenizationClient.tokenize(fragment, paymentMethodBuilder,
                new PaymentMethodResponseCallback() {
                    @Override
                    public void success(PaymentMethod paymentMethod) {
                        fragment.postCallback(paymentMethod);
                        sendAnalyticEvent("nonce-received");
                    }

                    @Override
                    public void failure(Exception exception) {
                        fragment.postCallback(exception);
                        sendAnalyticEvent("nonce-failed");
                    }

                    public void sendAnalyticEvent(String event) {
                        if (paymentMethodBuilder instanceof CardBuilder) {
                            fragment.sendAnalyticsEvent("card." + event);
                        } else if (paymentMethodBuilder instanceof PayPalAccountBuilder) {
                            fragment.sendAnalyticsEvent("paypal." + event);
                        }
                    }
                });
    }

    static String versionedPath(String path) {
        return "/v1/" + path;
    }
}
