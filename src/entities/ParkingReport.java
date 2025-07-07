package entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Represents a parking report in the ParkB system. Contains statistical data
 * about parking usage, subscriber status, and system performance.
 */
public class ParkingReport implements Serializable {

	private static final long serialVersionUID = 1L;

	/** The type of the report (e.g., "PARKING_TIME", "SUBSCRIBER_STATUS"). */
	private String reportType;

	/** The date the report refers to. */
	private LocalDate reportDate;

	// Parking Time Report fields

	/** Total number of parking sessions. */
	private int totalParkings;

	/** Average parking duration in minutes. */
	private double averageParkingTime;

	/** Number of late exits. */
	private int lateExits;

	/** Number of parking extensions. */
	private int extensions;

	/** Minimum parking time recorded. */
	private int minParkingTime;

	/** Maximum parking time recorded. */
	private int maxParkingTime;

	/** Number of immediate (non-reserved) parkings. */
	private int imidiateParkings;

	// Subscriber Status Report fields

	/** Number of active subscribers. */
	private int activeSubscribers;

	/** Total number of orders made. */
	private int totalOrders;

	/** Number of reservations made. */
	private int reservations;

	/** Number of immediate entries (non-reserved). */
	private int immediateEntries;

	/** Number of cancelled reservations. */
	private int cancelledReservations;

	/** Average session duration in minutes. */
	private double averageSessionDuration;

	// --- Fields for graphs ---

	/** Map of total parking time per day (day -> minutes). */
	private Map<String, Integer> totalParkingTimePerDay;

	/** Map of hourly distribution (hour -> count). */
	private Map<String, Integer> hourlyDistribution;

	/** Number of parkings without extensions. */
	private int noExtensions;

	/** Map of late exits by hour (hour -> count). */
	private Map<String, Integer> lateExitsByHour;

	/** Number of late subscribers. */
	private int lateSubscribers;

	/** Total number of subscribers. */
	private int totalSubscribers;

	/** Map of subscribers per day (day -> count). */
	private Map<String, Integer> subscribersPerDay;

	/** Number of used reservations. */
	private int usedReservations;

	/** Number of pre-order reservations. */
	private int preOrderReservations;

	/** Total hours in the reported month. */
	private int totalMonthHours;

	/** Number of occupied parking spots. */
	private int occupied;

	/** Default constructor. */
	public ParkingReport() {
	}

	/**
	 * Constructs a ParkingReport with report type and date.
	 *
	 * @param reportType the type of the report
	 * @param reportDate the date of the report
	 */
	public ParkingReport(String reportType, LocalDate reportDate) {
		this.reportType = reportType;
		this.reportDate = reportDate;
	}

	/** @return the report type */
	public String getReportType() {
		return reportType;
	}

	/** @param reportType the report type to set */
	public void setReportType(String reportType) {
		this.reportType = reportType;
	}

	/** @return the report date */
	public LocalDate getReportDate() {
		return reportDate;
	}

	/** @param reportDate the report date to set */
	public void setReportDate(LocalDate reportDate) {
		this.reportDate = reportDate;
	}

	/** @return total number of parkings */
	public int getTotalParkings() {
		return totalParkings;
	}

	/** @param totalParkings total number of parkings to set */
	public void setTotalParkings(int totalParkings) {
		this.totalParkings = totalParkings;
	}

	/** @return average parking time in minutes */
	public double getAverageParkingTime() {
		return averageParkingTime;
	}

	/** @param averageParkingTime average parking time to set */
	public void setAverageParkingTime(double averageParkingTime) {
		this.averageParkingTime = averageParkingTime;
	}

	/** @return number of late exits */
	public int getLateExits() {
		return lateExits;
	}

	/** @param lateExits number of late exits to set */
	public void setLateExits(int lateExits) {
		this.lateExits = lateExits;
	}

	/** @return number of extensions */
	public int getExtensions() {
		return extensions;
	}

	/** @param extensions number of extensions to set */
	public void setExtensions(int extensions) {
		this.extensions = extensions;
	}

	/** @return minimum parking time */
	public int getMinParkingTime() {
		return minParkingTime;
	}

	/** @param minParkingTime minimum parking time to set */
	public void setMinParkingTime(int minParkingTime) {
		this.minParkingTime = minParkingTime;
	}

	/** @return maximum parking time */
	public int getMaxParkingTime() {
		return maxParkingTime;
	}

	/** @param maxParkingTime maximum parking time to set */
	public void setMaxParkingTime(int maxParkingTime) {
		this.maxParkingTime = maxParkingTime;
	}

	/** @return number of active subscribers */
	public int getActiveSubscribers() {
		return activeSubscribers;
	}

	/** @param activeSubscribers number of active subscribers to set */
	public void setActiveSubscribers(int activeSubscribers) {
		this.activeSubscribers = activeSubscribers;
	}

	/** @return total number of orders */
	public int getTotalOrders() {
		return totalOrders;
	}

	/** @param totalOrders total number of orders to set */
	public void setTotalOrders(int totalOrders) {
		this.totalOrders = totalOrders;
	}

	/** @return number of reservations */
	public int getReservations() {
		return reservations;
	}

	/** @param reservations number of reservations to set */
	public void setReservations(int reservations) {
		this.reservations = reservations;
	}

	/** @return number of immediate entries */
	public int getImmediateEntries() {
		return immediateEntries;
	}

	/** @param immediateEntries number of immediate entries to set */
	public void setImmediateEntries(int immediateEntries) {
		this.immediateEntries = immediateEntries;
	}

	/** @return number of cancelled reservations */
	public int getCancelledReservations() {
		return cancelledReservations;
	}

	/** @param cancelledReservations number of cancelled reservations to set */
	public void setCancelledReservations(int cancelledReservations) {
		this.cancelledReservations = cancelledReservations;
	}

	/** @return average session duration */
	public double getAverageSessionDuration() {
		return averageSessionDuration;
	}

	/** @param averageSessionDuration average session duration to set */
	public void setAverageSessionDuration(double averageSessionDuration) {
		this.averageSessionDuration = averageSessionDuration;
	}

	/** @return formatted report date (yyyy-MM-dd) */
	public String getFormattedReportDate() {
		if (reportDate != null) {
			return reportDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		}
		return "";
	}

	/** @return formatted average parking time in hours and minutes */
	public String getFormattedAverageParkingTime() {
		long hours = (long) (averageParkingTime / 60);
		long minutes = (long) (averageParkingTime % 60);
		return String.format("%d hours, %d minutes", hours, minutes);
	}

	/** @return late exit percentage (scale of 0-10) */
	public double getLateExitPercentage() {
		if (totalParkings > 0) {
			return (double) lateExits / totalParkings * 10;
		}
		return 0.0;
	}

	/** @return extension percentage (scale of 0-10) */
	public double getExtensionPercentage() {
		if (totalParkings > 0) {
			return (double) extensions / totalParkings * 10;
		}
		return 0.0;
	}

	/** @return reservation percentage (scale of 0-10) */
	public double getReservationPercentage() {
		if (totalOrders > 0) {
			return (double) reservations / totalOrders * 10;
		}
		return 0.0;
	}

	/** @return string representation of ParkingReport */
	@Override
	public String toString() {
		return "ParkingReport{" + "reportType='" + reportType + '\'' + ", reportDate=" + reportDate + ", totalParkings="
				+ totalParkings + ", averageParkingTime=" + averageParkingTime + ", lateExits=" + lateExits
				+ ", extensions=" + extensions + ", activeSubscribers=" + activeSubscribers + ", totalOrders="
				+ totalOrders + ", reservations=" + reservations + ", immediateEntries=" + immediateEntries + '}';
	}

	/** @return total parking time per day */
	public Map<String, Integer> getTotalParkingTimePerDay() {
		return totalParkingTimePerDay;
	}

	/** @param m total parking time per day to set */
	public void setTotalParkingTimePerDay(java.util.Map<String, Integer> m) {
		this.totalParkingTimePerDay = m;
	}

	/** @return hourly distribution */
	public Map<String, Integer> getHourlyDistribution() {
		return hourlyDistribution;
	}

	/** @param m hourly distribution to set */
	public void setHourlyDistribution(java.util.Map<String, Integer> m) {
		this.hourlyDistribution = m;
	}

	/** @return number of parkings without extensions */
	public int getNoExtensions() {
		return noExtensions;
	}

	/** @param noExtensions number of no-extensions to set */
	public void setNoExtensions(int noExtensions) {
		this.noExtensions = noExtensions;
	}

	/** @return late exits by hour */
	public Map<String, Integer> getLateExitsByHour() {
		return lateExitsByHour;
	}

	/** @param m late exits by hour to set */
	public void setLateExitsByHour(java.util.Map<String, Integer> m) {
		this.lateExitsByHour = m;
	}

	/** @return number of late subscribers */
	public int getLateSubscribers() {
		return lateSubscribers;
	}

	/** @param lateSubscribers number of late subscribers to set */
	public void setLateSubscribers(int lateSubscribers) {
		this.lateSubscribers = lateSubscribers;
	}

	/** @return total number of subscribers */
	public int getTotalSubscribers() {
		return totalSubscribers;
	}

	/** @param totalSubscribers total subscribers to set */
	public void setTotalSubscribers(int totalSubscribers) {
		this.totalSubscribers = totalSubscribers;
	}

	/** @return subscribers per day */
	public Map<String, Integer> getSubscribersPerDay() {
		return subscribersPerDay;
	}

	/** @param m subscribers per day to set */
	public void setSubscribersPerDay(java.util.Map<String, Integer> m) {
		this.subscribersPerDay = m;
	}

	/** @return used reservations */
	public int getUsedReservations() {
		return usedReservations;
	}

	/** @param usedReservations used reservations to set */
	public void setUsedReservations(int usedReservations) {
		this.usedReservations = usedReservations;
	}

	/** @return total month hours */
	public int getTotalMonthHours() {
		return totalMonthHours;
	}

	/** @param totalMonthHours total month hours to set */
	public void setTotalMonthHours(int totalMonthHours) {
		this.totalMonthHours = totalMonthHours;
	}

	/** @return pre-order reservations */
	public int getpreOrderReservations() {
		return preOrderReservations;
	}

	/** @param preOrderReservations pre-order reservations to set */
	public void setpreOrderReservations(int preOrderReservations) {
		this.preOrderReservations = preOrderReservations;
	}

	/** @return occupied spots */
	public int getOccupied() {
		return occupied;
	}

	/** @param imidiateParkings number of occupied spots to set (stored in imidiateParkings) */
	public void setOccupied(int imidiateParkings) {
		this.imidiateParkings = imidiateParkings;
	}

	/** @return number of immediate parkings (actually returns occupied) */
	public int getImidiateParkings() {
		return occupied;
	}

	/** @param imidiateParkings number of immediate parkings to set */
	public void setImidiateParkings(int imidiateParkings) {
		this.imidiateParkings = imidiateParkings;
	}
}
