package org.openinstitute.finance;

import java.util.Date;
import org.openedit.Data;
import org.openedit.MultiValued;
import org.openedit.data.BaseData;
import org.openedit.util.DateStorageUtil;

public class BankTransaction extends BaseData
{

	boolean fieldBePositive = false;

	protected boolean isBePositive()
	{
		return fieldBePositive;
	}

	protected void setBePositive(boolean inBePositive)
	{
		fieldBePositive = inBePositive;
	}

	public BankTransaction(String inSearchType, MultiValued inData) {
		setSearchType(inSearchType);
		setData(inData);
	}

	public BankTransaction() {}

	String fieldSearchType;

	public String getSearchType()
	{
		return fieldSearchType;
	}

	public void setSearchType(String inSearchType)
	{
		fieldSearchType = inSearchType;
	}

	MultiValued fieldData;

	public MultiValued getData()
	{
		return fieldData;
	}

	public void setData(MultiValued inData)
	{
		fieldData = inData;
		// setProperties(fieldData.getProperties());
	}

	public Date getDate()
	{
		Date date = null;
		if (getSearchType().equals("transaction"))
		{
			date = getData().getDate("paymentdate");
		}
		else
			if (getSearchType().equals("collectivereimbursement"))
			{
				date = getData().getDate("paymentdate");
			}
			else
				if (getSearchType().equals("collectiveinvoice"))
				{
					date = getData().getDate("invoicepaidon");
				}
				else
				{
					date = getData().getDate("date");
				}
		if (date == null)
		{
			return new Date(0);
		}
		return date;
	}

	public Double getAmount()
	{
		Double t = 0D;
		if (getSearchType().equals("collectiveexpense") || getSearchType().equals("collectivereimbursement"))
		{
			t = 0 - getData().getDouble("total");
		}
		else
			if (getSearchType().equals("collectiveincome") || getSearchType().equals("collectiveinvestment"))
			{
				t = getData().getDouble("total");
			}
			else
			{
				t = getData().getDouble("totalprice");
			}
		if (isBePositive())
		{
			t = Math.abs(t);
		}
		return t;
	}

	public String getCollectionId()
	{
		String collectionid = getData().get("collectionid");
		return collectionid;
	}

	public String getCurrencyType()
	{
		String currencytype = getData().get("currencytype");
		if (currencytype == null)
		{
			currencytype = "1";
		}
		return currencytype;
	}

	@Override
	public Object getValue(String inType)
	{
		if (inType.equals("name"))
		{
			return getName();
		}
		if (inType.equals("total"))
		{
			return getAmount();
		}
		if (inType.equals("date"))
		{
			return DateStorageUtil.getStorageUtil().formatForStorage(getDate());
		}
		if (inType.equals("searchtype"))
		{
			return getSearchType();
		}
		Object value = getData().getValue(inType);
		if (value == null && inType.equals("currencytype"))
		{
			return "1";
		}
		return value;
	}

	public String getName()
	{
		String name = null;
		if (getSearchType().equals("transaction"))
		{
			name = getData().get("paymentemail");
		}
		else
			if (getSearchType().equals("collectiveexpense"))
			{
				name = getData().get("expensedescription");
			}
			else
				if (getSearchType().equals("collectivereimbursement"))
				{
					name = getData().get("expensedescription");
				}
				else
					if (getSearchType().equals("collectiveincome"))
					{
						name = getData().get("incomedescription");
					}
					else
						if (getSearchType().equals("collectiveinvoice"))
						{
							name = getData().get("name");
							if (name == null)
							{
								name = getData().get("invoicedescription");
							}
							if (name == null)
							{
								name = "#" + getData().get("invoicenumber");
							}
						}
						else
							if (getSearchType().equals("collectiveinvestment"))
							{
								name = getData().get("notes");
								if (name == null)
								{
									name = getData().getId();
								}
							}
							else
							{
								name = getSearchType();
							}
		return name;
	}
}
