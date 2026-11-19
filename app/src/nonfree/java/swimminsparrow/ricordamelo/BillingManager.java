package swimminsparrow.ricordamelo;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.revenuecat.purchases.CustomerInfo;
import com.revenuecat.purchases.EntitlementInfo;
import com.revenuecat.purchases.LogLevel;
import com.revenuecat.purchases.Offering;
import com.revenuecat.purchases.Offerings;
import com.revenuecat.purchases.Package;
import com.revenuecat.purchases.PurchaseParams;
import com.revenuecat.purchases.Purchases;
import com.revenuecat.purchases.PurchasesConfiguration;
import com.revenuecat.purchases.PurchasesError;
import com.revenuecat.purchases.interfaces.PurchaseCallback;
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback;
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback;
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener;
import com.revenuecat.purchases.models.StoreTransaction;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.SettingsDB;

/**
 * Internal BillingManager for Google Play flavor via RevenueCat.
 * Isolated completely inside nonfree flavor.
 */
public class BillingManager {

    public interface BillingCallback {
        void onSuccess(String message);
        void onError(String message);
        void onCanceled();
    }

    public interface StateListener {
        void onProStatusChanged(boolean isPro);
    }

    private static final String TAG = "PillSOS_Billing";
    public static final String ENTITLEMENT_PRO = "pillsos_pro";
    public static final String ENTITLEMENT_PRO_FALLBACK = "pro";
    public static final String ENTITLEMENT_REMOVE_ADS = "remove_ads";

    private static BillingManager instance;

    private boolean isRevenueCatConfigured = false;
    private Package lifetimePackage;
    private String formattedPrice = BuildConfig.DEFAULT_PRO_PRICE;
    private final List<StateListener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized BillingManager getInstance() {
        if (instance == null) {
            instance = new BillingManager();
        }
        return instance;
    }

    private BillingManager() {
    }

    public synchronized void init(Activity activity) {
        if (activity == null) return;
        Context context = activity.getApplicationContext();

        String apiKey = BuildConfig.DEBUG ? BuildConfig.REVENUECAT_KEY_DEBUG : BuildConfig.REVENUECAT_KEY_RELEASE;
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("placeholder")) {
            Log.w(TAG, "RevenueCat API Key non configurata in BuildConfig. Modalità simulata attiva.");
            isRevenueCatConfigured = false;
            return;
        }

        try {
            if (!Purchases.isConfigured()) {
                Purchases.setLogLevel(BuildConfig.DEBUG ? LogLevel.DEBUG : LogLevel.INFO);
                PurchasesConfiguration config = new PurchasesConfiguration.Builder(context, apiKey.trim()).build();
                Purchases.configure(config);
            }

            isRevenueCatConfigured = true;
            Log.d(TAG, "RevenueCat configurato con successo. Sandbox/Debug: " + BuildConfig.DEBUG);

            Purchases.getSharedInstance().setUpdatedCustomerInfoListener(new UpdatedCustomerInfoListener() {
                @Override
                public void onReceived(@NonNull CustomerInfo customerInfo) {
                    handleCustomerInfo(context, customerInfo);
                }
            });

            // Fetch initial offerings and customer status
            fetchOfferings();
            refreshCustomerInfo(context);

        } catch (Exception e) {
            isRevenueCatConfigured = false;
            Log.e(TAG, "Errore inizializzazione RevenueCat: " + e.getMessage(), e);
        }
    }

    private void fetchOfferings() {
        if (!isRevenueCatConfigured) return;
        Purchases.getSharedInstance().getOfferings(new ReceiveOfferingsCallback() {
            @Override
            public void onReceived(@NonNull Offerings offerings) {
                Offering current = offerings.getCurrent();
                if (current != null) {
                    Package pkg = current.getLifetime();
                    if (pkg == null && current.getAvailablePackages() != null) {
                        for (Package p : current.getAvailablePackages()) {
                            if (p.getIdentifier().toLowerCase().contains("lifetime")) {
                                pkg = p;
                                break;
                            }
                        }
                        if (pkg == null && !current.getAvailablePackages().isEmpty()) {
                            pkg = current.getAvailablePackages().get(0);
                        }
                    }

                    if (pkg != null) {
                        lifetimePackage = pkg;
                        if (pkg.getProduct() != null && pkg.getProduct().getPrice() != null) {
                            String price = pkg.getProduct().getPrice().getFormatted();
                            if (price != null && !price.trim().isEmpty()) {
                                formattedPrice = price;
                            }
                        }
                        Log.d(TAG, "Pacchetto Pro caricato: " + pkg.getIdentifier() + " - Prezzo: " + formattedPrice);
                        mainHandler.post(() -> notifyStateChanged(isPro()));
                    }
                }
            }

            @Override
            public void onError(@NonNull PurchasesError error) {
                Log.w(TAG, "Errore recupero offerte RevenueCat: " + error.getMessage());
            }
        });
    }

    private void refreshCustomerInfo(Context context) {
        if (!isRevenueCatConfigured) return;
        Purchases.getSharedInstance().getCustomerInfo(new ReceiveCustomerInfoCallback() {
            @Override
            public void onReceived(@NonNull CustomerInfo customerInfo) {
                handleCustomerInfo(context, customerInfo);
            }

            @Override
            public void onError(@NonNull PurchasesError error) {
                Log.w(TAG, "Errore recupero customer info: " + error.getMessage());
            }
        });
    }

    private boolean isEntitlementActive(CustomerInfo customerInfo) {
        if (customerInfo == null || customerInfo.getEntitlements() == null) {
            return false;
        }
        EntitlementInfo mainEntitlement = customerInfo.getEntitlements().get(ENTITLEMENT_PRO);
        if (mainEntitlement != null && mainEntitlement.isActive()) return true;

        EntitlementInfo fallbackEntitlement = customerInfo.getEntitlements().get(ENTITLEMENT_PRO_FALLBACK);
        if (fallbackEntitlement != null && fallbackEntitlement.isActive()) return true;

        EntitlementInfo removeAdsEntitlement = customerInfo.getEntitlements().get(ENTITLEMENT_REMOVE_ADS);
        return removeAdsEntitlement != null && removeAdsEntitlement.isActive();
    }

    private void handleCustomerInfo(Context context, CustomerInfo customerInfo) {
        boolean isProActive = isEntitlementActive(customerInfo);
        Log.d(TAG, "handleCustomerInfo: isProActive=" + isProActive);
        if (isProActive) {
            SettingsDB.getInstance(context).setAdFreeUnlocked(true);
            notifyStateChanged(true);
        }
    }

    public boolean isPro() {
        return SettingsDB.getInstance(null).isAdFreeUnlocked();
    }

    public boolean isAvailable() {
        return true;
    }

    public String getFormattedPrice() {
        return formattedPrice;
    }

    public void purchasePro(Activity activity, BillingCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("Attività non disponibile");
            return;
        }

        if (!isRevenueCatConfigured) {
            Log.w(TAG, "RevenueCat non configurato. Esecuzione simulazione d'acquisto per sviluppo.");
            SettingsDB.getInstance(activity).setAdFreeUnlocked(true);
            notifyStateChanged(true);
            if (callback != null) {
                mainHandler.post(() -> callback.onSuccess("Simulazione: Versione Pro attivata con successo!"));
            }
            return;
        }

        if (lifetimePackage == null) {
            Log.w(TAG, "Nessun pacchetto disponibile da RevenueCat al momento.");
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Offerta non caricata. Verifica la connessione e riprova."));
            }
            return;
        }

        PurchaseParams params = new PurchaseParams.Builder(activity, lifetimePackage).build();
        Purchases.getSharedInstance().purchase(params, new PurchaseCallback() {
            @Override
            public void onCompleted(@NonNull StoreTransaction storeTransaction, @NonNull CustomerInfo customerInfo) {
                boolean proUnlocked = isEntitlementActive(customerInfo);
                Log.d(TAG, "Acquisto completato: proUnlocked=" + proUnlocked);
                if (proUnlocked) {
                    SettingsDB.getInstance(activity).setAdFreeUnlocked(true);
                    notifyStateChanged(true);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess("Versione Pro sbloccata con successo!"));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess("Acquisto completato."));
                    }
                }
            }

            @Override
            public void onError(@NonNull PurchasesError error, boolean userCancelled) {
                Log.w(TAG, "Errore acquisto: " + error.getMessage() + " (userCancelled=" + userCancelled + ")");
                if (callback != null) {
                    mainHandler.post(() -> {
                        if (userCancelled) {
                            callback.onCanceled();
                        } else {
                            callback.onError(error.getMessage());
                        }
                    });
                }
            }
        });
    }

    public void restorePurchases(Activity activity, BillingCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onError("Attività non disponibile");
            return;
        }

        if (!isRevenueCatConfigured) {
            boolean alreadyPro = SettingsDB.getInstance(activity).isAdFreeUnlocked();
            if (callback != null) {
                mainHandler.post(() -> {
                    if (alreadyPro) {
                        callback.onSuccess("Versione Pro già attiva (simulazione).");
                    } else {
                        callback.onError("Nessun acquisto precedente trovato (simulazione).");
                    }
                });
            }
            return;
        }

        Purchases.getSharedInstance().restorePurchases(new ReceiveCustomerInfoCallback() {
            @Override
            public void onReceived(@NonNull CustomerInfo customerInfo) {
                boolean proUnlocked = isEntitlementActive(customerInfo);
                if (proUnlocked) {
                    SettingsDB.getInstance(activity).setAdFreeUnlocked(true);
                    notifyStateChanged(true);
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess("Acquisto ripristinato con successo!"));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError("Nessun acquisto attivo trovato da ripristinare."));
                    }
                }
            }

            @Override
            public void onError(@NonNull PurchasesError error) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(error.getMessage()));
                }
            }
        });
    }

    public synchronized void addStateListener(StateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeStateListener(StateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private synchronized void notifyStateChanged(boolean isPro) {
        for (StateListener listener : new ArrayList<>(listeners)) {
            if (listener != null) {
                mainHandler.post(() -> listener.onProStatusChanged(isPro));
            }
        }
    }
}
