package org.openinstitute.finance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MultiCurrency
{
	String fieldKeyedOn;

	public String getKeyedOn()
	{
		return fieldKeyedOn;
	}

	public void setKeyedOn(String inKeyedOn)
	{
		fieldKeyedOn = inKeyedOn;
	}

	public Map<String, Double> getValues()
	{
		return fieldValues;
	}

	public void setValues(Map inValues)
	{
		fieldValues = inValues;
	}

	Map fieldValues = new HashMap();

	public Double addTo(String inCurrency, Double inValue)
	{
		Double existing = getValues().get(inCurrency);
		if (existing == null)
		{
			existing = 0D;
		}
		existing = existing + inValue;
		getValues().put(inCurrency, existing);
		return existing;
	}

	public void addAll(MultiCurrency inCurrency)
	{
		for (Iterator iterator = inCurrency.getValues().keySet().iterator(); iterator.hasNext();)
		{
			String currency = (String) iterator.next();
			addTo(currency, inCurrency.getValues().get(currency));
		}
	}

	public Collection getCurrencies()
	{
		// Sort?
		List currencies = new ArrayList(getValues().keySet());
		return currencies;
	}

	public Double getValue(String inCurrency)
	{
		return getValues().get(inCurrency);
	}
}
