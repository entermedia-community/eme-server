package org.openinstitute.finance;

import java.time.LocalDate;

public class BookingDate
{
	protected LocalDate fieldLocalDate;

	public LocalDate getLocalDate()
	{
		return fieldLocalDate;
	}

	public void setLocalDate(LocalDate inLocalDate)
	{
		fieldLocalDate = inLocalDate;
	}

	protected boolean isAvailable;

	public BookingDate() {
		// TODO Auto-generated constructor stub
	}

	public BookingDate(LocalDate inDate) {
		setLocalDate(inDate);
	}

	public boolean isAvailable()
	{
		return isAvailable;
	}

	public void setAvailable(boolean inIsAvailable)
	{
		isAvailable = inIsAvailable;
	}

	public String toDateString()
	{
		StringBuffer formated = new StringBuffer();
		formated.append(getLocalDate().getYear());
		formated.append("-");
		formated.append(String.format("%02d", getLocalDate().getMonthValue()));
		formated.append("-");
		formated.append(String.format("%02d", getLocalDate().getDayOfMonth()));

		return formated.toString();
	}

	public String toString()
	{
		return "[" + toDateString() + "]";
	}
}
