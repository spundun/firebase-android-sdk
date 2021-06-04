package com.google.firebase.dynamiclinks.internal;

import android.net.Uri;
import android.os.Parcel;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.safeparcel.AbstractSafeParcelable;
import com.google.android.gms.common.internal.safeparcel.SafeParcelable;
import com.google.firebase.dynamiclinks.ShortDynamicLink;
import java.util.ArrayList;
import java.util.List;

/** {@link SafeParcelable} implementation of {@link ShortDynamicLink}. */
@SafeParcelable.Class(creator = "ShortDynamicLinkImplCreator")
public final class ShortDynamicLinkImpl extends AbstractSafeParcelable implements ShortDynamicLink {

  public static final Creator<ShortDynamicLinkImpl> CREATOR = new ShortDynamicLinkImplCreator();

  @SafeParcelable.Field(id = 1, getter = "getShortLink")
  @Nullable
  private final Uri shortLink;

  @SafeParcelable.Field(id = 2, getter = "getPreviewLink")
  @Nullable
  private final Uri previewLink;

  @SafeParcelable.Field(id = 3, getter = "getWarnings")
  private final List<WarningImpl> warnings;

  @SafeParcelable.Constructor
  public ShortDynamicLinkImpl(
      @Nullable @Param(id = 1) Uri shortLink,
      @Nullable @Param(id = 2) Uri previewLink,
      @Nullable @Param(id = 3) List<WarningImpl> warnings) {
    this.shortLink = shortLink;
    this.previewLink = previewLink;
    this.warnings = warnings == null ? new ArrayList<>() : warnings;
  }

  @Override
  @Nullable
  public Uri getShortLink() {
    return shortLink;
  }

  @Override
  @Nullable
  public Uri getPreviewLink() {
    return previewLink;
  }

  @Override
  public List<WarningImpl> getWarnings() {
    return warnings;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    ShortDynamicLinkImplCreator.writeToParcel(this, dest, flags);
  }

  /** {@link SafeParcelable} implementation of {@link Warning}. */
  @SafeParcelable.Class(creator = "WarningImplCreator")
  public static class WarningImpl extends AbstractSafeParcelable implements Warning {

    public static final Creator<WarningImpl> CREATOR = new WarningImplCreator();

    @SafeParcelable.Reserved({1 /* code, deprecated */})
    @SafeParcelable.Field(id = 2, getter = "getMessage")
    private final String message;

    @SafeParcelable.Constructor
    public WarningImpl(@Param(id = 2) String message) {
      this.message = message;
    }

    @Override
    @Nullable
    public String getCode() {
      // warningCode deprecated on server, returns non-useful, hard-coded value.
      return null;
    }

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      WarningImplCreator.writeToParcel(this, dest, flags);
    }
  }
}
