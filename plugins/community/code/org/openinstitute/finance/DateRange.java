package org.openinstitute.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.util.DateStorageUtil;

public class DateRange
{
	private static final Log log = LogFactory.getLog(DateRange.class);
	Collection<BookingDate> fieldBlockedDates;

	public Collection<BookingDate> getBlockedDates()
	{
		if (fieldBlockedDates == null)
		{
			fieldBlockedDates = new ArrayList<BookingDate>();
		}

		return fieldBlockedDates;
	}

	public void setBlockedDates(Collection<BookingDate> inBlockedDates)
	{
		fieldBlockedDates = inBlockedDates;
	}

	Date fieldStartDate;

	boolean fieldAllTime;
	int fieldYearPicked;

	public int getYearPicked()
	{
		return fieldYearPicked;
	}

	public void setYearPicked(int inYearPicked)
	{
		fieldYearPicked = inYearPicked;
	}

	public int getMonthPicked()
	{
		return fieldMonthPicked;
	}

	public void setMonthPicked(int inMonthPicked)
	{
		fieldMonthPicked = inMonthPicked;
	}

	int fieldMonthPicked;

	public boolean isAllTime()
	{
		return fieldAllTime;
	}

	public void setAllTime(boolean inAllTime)
	{
		fieldAllTime = inAllTime;
	}

	public Date getStartDate()
	{
		return fieldStartDate;
	}

	public void setStartDate(Date inStartDate)
	{
		fieldStartDate = inStartDate;
	}

	public Date getEndDate()
	{
		return fieldEndDate;
	}

	public void setEndDate(Date inEndDate)
	{
		fieldEndDate = inEndDate;
	}

	Date fieldEndDate;

	public void setYearAndMonth(int yearsback, int inMonth)
	{
		setYearPicked(yearsback);
		setMonthPicked(inMonth);
		Calendar cal = DateStorageUtil.getStorageUtil().createCalendar(); // Use the server time. That is what they are
																			// searching for on the server
		int year = cal.get(Calendar.YEAR);
		cal.set(Calendar.YEAR, year - yearsback);
		cal.set(Calendar.MONTH, inMonth - 1);
		cal.set(Calendar.DAY_OF_MONTH, 1);

		Date start = cal.getTime();
		setStartDate(start);
		cal.add(Calendar.MONTH, 1);
		cal.add(Calendar.DAY_OF_MONTH, -1);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.HOUR_OF_DAY, 23);

		Date onemonth = cal.getTime();
		setEndDate(onemonth);
	}

	public void setYearToDate(int yearsback)
	{
		setYearPicked(yearsback);
		setMonthPicked(0);
		Calendar cal = DateStorageUtil.getStorageUtil().createCalendar();

		int year = cal.get(Calendar.YEAR);
		cal.set(Calendar.YEAR, year - yearsback);
		cal.set(Calendar.MONTH, 0);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		Date start = cal.getTime();
		setStartDate(start);
		int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
		cal.set(Calendar.YEAR, year - yearsback);
		cal.set(Calendar.MONTH, 11);
		cal.set(Calendar.DAY_OF_MONTH, days);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.HOUR_OF_DAY, 23);

		Date onemonth = cal.getTime();
		setEndDate(onemonth);
	}

	public void addBlockedDateRange(Date inStartDate, Date inEndDate)
	{
		LocalDateTime date1 = toLocalDate(inStartDate);

		LocalDateTime date2 = toLocalDate(inEndDate);
		if (date2.getYear() < 1000)
		{
			date2 = date2.plusYears(2000);
		}
		long daysBetween = ChronoUnit.DAYS.between(date1, date2);
		LocalDate starting = date1.toLocalDate();
		log.info("Blocking " + starting + " for " + daysBetween + " days:" + date1 + " - " + date2);
		for (int i = 0; i < daysBetween; i++)
		{
			BookingDate newdate = new BookingDate(starting);
			newdate.setAvailable(false);
			getBlockedDates().add(newdate);
			starting = starting.plusDays(1);
		}
	}

	protected LocalDateTime toLocalDate(Date inDate)
	{
		return Instant.ofEpochMilli(inDate.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
	}
}
