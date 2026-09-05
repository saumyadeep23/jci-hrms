package in.gov.jci.hrms.entity;

/**
 * ALMS operational gap #3 - device registration. MOBILE was split into
 * ANDROID_MOBILE/IOS_MOBILE and the two desktop/web variants were added per
 * UAT feedback (device_type is a plain VARCHAR(30) column, V38 - no CHECK
 * constraint and no migration needed for this widening).
 */
public enum DeviceType {
    ANDROID_MOBILE,
    IOS_MOBILE,
    LAPTOP_DESKTOP_WEB,
    LAPTOP_DESKTOP_CLIENT,
    BIOMETRIC_TERMINAL
}
