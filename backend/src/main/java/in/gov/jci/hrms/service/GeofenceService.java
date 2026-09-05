package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.RegionalOffice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * FR-MOB.4: validates a punch's reported coordinates against the geofence
 * radius configured on the employee's assigned office, using the Haversine
 * great-circle distance formula. The employee's DPC takes priority when it
 * has geo data configured; otherwise falls back to their RO's own geo data
 * (both DepartmentalPurchaseCentre and RegionalOffice carry lat/long/
 * geofence columns).
 *
 * If neither has geofence data configured (or the employee has neither
 * assigned - e.g. HO-posted), there is no boundary to check against, so the
 * punch is treated as within bounds rather than flagged - an unconfigured
 * geofence isn't evidence of a suspicious punch. isGeofenceExempted always
 * wins regardless of what's configured (FR-MOB.4 exemption toggle).
 */
@Service
public class GeofenceService {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    /**
     * DPC's own geofence takes priority when configured; otherwise falls
     * back to the employee's RO (an HO-posted employee, or one whose RO/DPC
     * has no geo data configured, always passes - no geofence to check
     * against). isGeofenceExempted always short-circuits to true first,
     * regardless of what geo data is configured - e.g. a field officer who
     * legitimately punches from outside any office's radius.
     */
    public boolean isWithinGeofence(Employee employee, BigDecimal latitude, BigDecimal longitude) {
        if (employee.isGeofenceExempted()) {
            return true;
        }
        GeoFence geoFence = resolveGeoFence(employee);
        if (geoFence == null) {
            return true;
        }
        double distance = distanceMeters(geoFence.latitude(), geoFence.longitude(), latitude, longitude);
        return distance <= geoFence.radiusMeters();
    }

    private record GeoFence(BigDecimal latitude, BigDecimal longitude, double radiusMeters) {
    }

    private GeoFence resolveGeoFence(Employee employee) {
        DepartmentalPurchaseCentre dpc = employee.getDepartmentalPurchaseCentre();
        if (dpc != null && dpc.getLatitude() != null && dpc.getLongitude() != null && dpc.getGeofenceRadiusMeters() != null) {
            return new GeoFence(dpc.getLatitude(), dpc.getLongitude(), dpc.getGeofenceRadiusMeters());
        }
        RegionalOffice regionalOffice = employee.getRegionalOffice();
        if (regionalOffice != null && regionalOffice.getLatitude() != null && regionalOffice.getLongitude() != null
                && regionalOffice.getGeofenceRadiusMeters() != null) {
            return new GeoFence(regionalOffice.getLatitude(), regionalOffice.getLongitude(),
                    regionalOffice.getGeofenceRadiusMeters().doubleValue());
        }
        return null;
    }

    public double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
