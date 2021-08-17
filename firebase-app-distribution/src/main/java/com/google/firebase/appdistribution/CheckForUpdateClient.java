// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution;

import static com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils.calculateApkInternalCodeHash;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

class CheckForUpdateClient {
  private static final int UPDATE_THREAD_POOL_SIZE = 4;

  private final FirebaseApp firebaseApp;
  private final FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private static final ConcurrentMap<String, String> cachedCodeHashes = new ConcurrentHashMap<>();
  private final ReleaseIdentifierStorage releaseIdentifierStorage;

  Task<AppDistributionReleaseInternal> cachedCheckForUpdate = null;
  private final Executor checkForUpdateExecutor;

  CheckForUpdateClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    // TODO: verify if this is best way to use executorservice here
    this.checkForUpdateExecutor = Executors.newFixedThreadPool(UPDATE_THREAD_POOL_SIZE);
    this.releaseIdentifierStorage =
        new ReleaseIdentifierStorage(firebaseApp.getApplicationContext());
  }

  CheckForUpdateClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull Executor executor) {
    this.firebaseApp = firebaseApp;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    // TODO: verify if this is best way to use executorservice here
    this.checkForUpdateExecutor = executor;
    this.releaseIdentifierStorage =
        new ReleaseIdentifierStorage(firebaseApp.getApplicationContext());
  }

  @NonNull
  public synchronized Task<AppDistributionReleaseInternal> checkForUpdate() {

    if (cachedCheckForUpdate != null && !cachedCheckForUpdate.isComplete()) {
      return cachedCheckForUpdate;
    }

    Task<String> installationIdTask = firebaseInstallationsApi.getId();
    // forceRefresh is false to get locally cached token if available
    Task<InstallationTokenResult> installationAuthTokenTask =
        firebaseInstallationsApi.getToken(false);

    this.cachedCheckForUpdate =
        Tasks.whenAllSuccess(installationIdTask, installationAuthTokenTask)
            .onSuccessTask(
                checkForUpdateExecutor,
                tasks -> {
                  String fid = installationIdTask.getResult();
                  InstallationTokenResult installationTokenResult =
                      installationAuthTokenTask.getResult();
                  try {
                    AppDistributionReleaseInternal latestRelease =
                        getLatestReleaseFromClient(
                            fid,
                            firebaseApp.getOptions().getApplicationId(),
                            firebaseApp.getOptions().getApiKey(),
                            installationTokenResult.getToken());
                    return Tasks.forResult(latestRelease);
                  } catch (FirebaseAppDistributionException ex) {
                    return Tasks.forException(ex);
                  }
                })
            .continueWithTask(
                checkForUpdateExecutor,
                task ->
                    TaskUtils.handleTaskFailure(
                        task,
                        Constants.ErrorMessages.NETWORK_ERROR,
                        FirebaseAppDistributionException.Status.NETWORK_FAILURE));

    return cachedCheckForUpdate;
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getLatestReleaseFromClient(
      String fid, String appId, String apiKey, String authToken)
      throws FirebaseAppDistributionException {
    try {
      AppDistributionReleaseInternal retrievedLatestRelease =
          firebaseAppDistributionTesterApiClient.fetchLatestRelease(fid, appId, apiKey, authToken);

      if (isNewerBuildVersion(retrievedLatestRelease)
          || !isInstalledRelease(retrievedLatestRelease)) {
        return retrievedLatestRelease;
      } else {
        // Return null if retrieved latest release is older or currently installed
        return null;
      }
    } catch (NumberFormatException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR,
          FirebaseAppDistributionException.Status.NETWORK_FAILURE,
          e);
    }
  }

  private boolean isNewerBuildVersion(AppDistributionReleaseInternal latestRelease)
      throws FirebaseAppDistributionException {
    return Long.parseLong(latestRelease.getBuildVersion())
        > getInstalledAppVersionCode(firebaseApp.getApplicationContext());
  }

  @VisibleForTesting
  boolean isInstalledRelease(AppDistributionReleaseInternal latestRelease) {
    if (latestRelease.getBinaryType().equals(BinaryType.APK)) {
      return latestAndInstalledReleaseSameCodeHash(latestRelease);
    }

    if (latestRelease.getIasArtifactId() == null) {
      return false;
    }
    // AAB BinaryType
    return latestRelease
        .getIasArtifactId()
        .equals(
            ReleaseIdentificationUtils.extractInternalAppSharingArtifactId(
                firebaseApp.getApplicationContext()));
  }

  private long getInstalledAppVersionCode(Context context) throws FirebaseAppDistributionException {
    PackageInfo pInfo;
    try {
      pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.UNKNOWN_ERROR,
          FirebaseAppDistributionException.Status.UNKNOWN,
          e);
    }
    return PackageInfoCompat.getLongVersionCode(pInfo);
  }

  @VisibleForTesting
  String extractApkCodeHash(PackageInfo packageInfo) {
    File sourceFile = new File(packageInfo.applicationInfo.sourceDir);

    String key =
        String.format(
            Locale.ENGLISH, "%s.%d", sourceFile.getAbsolutePath(), sourceFile.lastModified());
    if (!cachedCodeHashes.containsKey(key)) {
      cachedCodeHashes.put(key, calculateApkInternalCodeHash(sourceFile));
    }
    return releaseIdentifierStorage.getExternalCodeHash(cachedCodeHashes.get(key));
  }

  private boolean latestAndInstalledReleaseSameCodeHash(
      AppDistributionReleaseInternal latestRelease) {
    try {
      Context context = firebaseApp.getApplicationContext();
      PackageInfo metadataPackageInfo =
          context
              .getPackageManager()
              .getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      String externalCodeHash = extractApkCodeHash(metadataPackageInfo);
      // Will trigger during the first install of the app since no zipHash to externalCodeHash
      // mapping will have been set in ReleaseIdentifierStorage yet
      if (externalCodeHash == null) {
        return false;
      }

      // If the codeHash for the retrieved latestRelease is equal to the stored codeHash
      // of the installed release, then they are the same release.
      return externalCodeHash.equals(latestRelease.getCodeHash());
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }
}
