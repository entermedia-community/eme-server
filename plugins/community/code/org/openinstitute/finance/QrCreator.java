package org.openinstitute.finance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.entermediadb.asset.MediaArchive;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.OpenEditException;
import org.openedit.users.User;
import org.openedit.util.Exec;
import org.openedit.util.ExecResult;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QrCreator implements CatalogEnabled
{

    protected ModuleManager fieldModuleManager;

    public ModuleManager getModuleManager()
    {
        return fieldModuleManager;
    }

    public void setModuleManager(ModuleManager inModuleManager)
    {
        fieldModuleManager = inModuleManager;
    }

    protected String fieldCatalogId;

    public String getCatalogId()
    {
        return fieldCatalogId;
    }

    public void setCatalogId(String inCatalogId)
    {
        fieldCatalogId = inCatalogId;
    }

    public MediaArchive getMediaArchive()
    {
        return (MediaArchive) getModuleManager().getBean(getCatalogId(), "mediaArchive");
    }

    public Collection createQrCodes() throws Exception
    {

        // Placeholder for QR code generation logic
        // In a real implementation, you would use a library like ZXing to generate the
        // QR code image
        Collection<Data> missingcodes = getMediaArchive().query("bankaccountlookup").exact("cardmade", false).exists("bankaccount").sort("idUp").search();
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        File saveto = new File(getMediaArchive().getRootDirectory(), "/cards/");
        saveto.mkdirs();

        Collection tosave = new ArrayList();

        for (Data data : missingcodes)
        {
            // Generate QR code for the bank account
            String bankAccountNumber = data.getId();
            String url = "https://impactbank.world/card/?l=" + bankAccountNumber;
            int width = 460; // 3.37 * 300
            int height = 460; // 2.14

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);

            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, width, height, hints);
            String qr = "qr_" + bankAccountNumber + ".png";
            Path path = new File(saveto, "qr_" + bankAccountNumber + ".png").toPath();
            MatrixToImageWriter.writeToPath(bitMatrix, "PNG", path);

            data.setValue("cardmade", true);

            makeImage(qr);

            User user = getMediaArchive().getUser(data.get("user"));

            addName(user, qr);
            tosave.add(data);
        }
        getMediaArchive().saveData("bankaccountlookup", tosave);

        Collection<Data> donecards = getMediaArchive().query("bankaccountlookup").exact("cardmade", true).exists("bankaccount").sort("idUp").search();
        return donecards;
    }

    protected void makeImage(String inQr) throws Exception
    {
        File input = new File(getMediaArchive().getRootDirectory(), "/cards/input.jpeg");
        File qrcode = new File(getMediaArchive().getRootDirectory(), "/cards/" + inQr);
        String outputName = inQr.replace("qr_", "card_");
        File output = new File(getMediaArchive().getRootDirectory(), "/cards/" + outputName);

        // int width = 1011; //3.37 * 300
        // int height = 639; //2.14

        long starttime = System.currentTimeMillis();
        ArrayList args = new ArrayList();
        args.add(input.getAbsolutePath());
        args.add(qrcode.getAbsolutePath());
        args.add("-gravity");
        args.add("northeast");
        args.add("-geometry");
        args.add("+20+20");
        args.add("-composite");
        args.add("-quality");
        args.add("90");
        args.add("-strip");
        // args.add("-resize");
        // args.add(String.valueOf(width) + "x" + String.valueOf(height));

        args.add(output.getAbsolutePath());

        Exec exec = (Exec) getMediaArchive().getBean("exec");
        ExecResult result = exec.runExec("convert", args, true);
        if (!result.isRunOk())
        {
            throw new OpenEditException("Error compositing image: " + result.getReturnValue());
        }
    }

    protected void addName(User inUser, String inQr) throws Exception
    {
        String inputName = inQr.replace("qr_", "card_");
        File input = new File(getMediaArchive().getRootDirectory(), "/cards/" + inputName);
        String outputName = inQr.replace("qr_", "carddone_");
        File output = new File(getMediaArchive().getRootDirectory(), "/cards/" + outputName);

        String screenname = inUser.getScreenName();
        if (screenname == null)
        {
            screenname = inUser.toString();
        }
        String email = inUser.getEmail();

        ArrayList args = new ArrayList();
        args.add(input.getAbsolutePath());
        args.add("-gravity");
        args.add("southwest");
        args.add("-pointsize");
        args.add("65");
        args.add("-fill");
        args.add("lightgray");
        args.add("-annotate");
        args.add("+30+80");
        args.add(screenname);
        args.add("-pointsize");
        args.add("40");
        args.add("-annotate");
        args.add("+30+30");
        args.add(email);
        args.add("-quality");
        args.add("90");
        args.add("-strip");

        args.add(output.getAbsolutePath());

        Exec exec = (Exec) getMediaArchive().getBean("exec");
        ExecResult result = exec.runExec("convert", args, true);
        if (!result.isRunOk())
        {
            throw new OpenEditException("Error compositing image: " + result.getReturnValue());
        }
    }
}
