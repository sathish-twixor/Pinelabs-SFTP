package com.inswit.sftp.service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MainReportService {

    private static final Logger logger = Logger.getLogger("MainReportService");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SftpService sftpService;

    @Value("${csv.pinelabs.event.file.upload}")
    private String uploadDirectory;

    private static final int BATCH_SIZE = 1000;

    private static final List<String> FIELDS = Arrays.asList(
        "api_pdf_url", "pdf_url", "udham_pdf_url", "pan_image", "rich_card_img",
        "gst_rich_card", "cheque_image", "po_rich_card"
    );

    private static final Map<String, String> FIELD_TO_HEADER;
    static {
        FIELD_TO_HEADER = new HashMap<>();
        FIELD_TO_HEADER.put("api_pdf_url", "API PDF URL");
        FIELD_TO_HEADER.put("pdf_url", "Agreement PDF URL");
        FIELD_TO_HEADER.put("udham_pdf_url", "Udyam PDF URL");
        FIELD_TO_HEADER.put("pan_image", "PAN Image");
        FIELD_TO_HEADER.put("rich_card_img", "Rich Card Image (Pan)");
        FIELD_TO_HEADER.put("gst_rich_card", "GST Rich Card");
        FIELD_TO_HEADER.put("cheque_image", "Cheque Image");
        FIELD_TO_HEADER.put("po_rich_card", "PO Rich Card");
    }

    public Map<String, Object> fetchAndStoreData() throws IOException {
        Map<String, Integer> downloadCounts = new HashMap<>();
        for (String field : FIELDS) {
            downloadCounts.put(field, 0);
        }

        Set<String> DOCUMENT_FIELDS = new HashSet<>(Arrays.asList("api_pdf_url", "pdf_url", "udham_pdf_url"));
        Set<String> IMAGE_FIELDS = new HashSet<>(Arrays.asList(
            "pan_image", "rich_card_img", "gst_rich_card", "cheque_image", "po_rich_card"
        ));

        LocalDate currentDate = LocalDate.now();
        String formattedDate = currentDate.minusDays(1).toString();
        String dateFolder = currentDate.toString();

        File baseDir = new File(uploadDirectory, formattedDate);
        File imageDir = new File(baseDir, "images");
        File docDir = new File(baseDir, "documents");

        imageDir.mkdirs();
        docDir.mkdirs();

        File excelFile = new File(baseDir, "main_report.xlsx");

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Report");
        writeHeader(sheet);

        int offset = 0;
        int rowIndex = 1;

        List<Map<String, Object>> rows = fetchBatch(formattedDate, offset, BATCH_SIZE);
        boolean hasData = !rows.isEmpty();

        while (!rows.isEmpty()) {
            for (Map<String, Object> row : rows) {
                Row excelRow = sheet.createRow(rowIndex++);
                int colIndex = 0;

                String[] excelFields = {
                    "updated_at", "merchant_name", "reg_mobile_number", "form_owner_name", "store_name", "email_id",
                    "no_of_outlets", "annual_turn_over", "store_manager_name", "store_manager_phone_number",
                    "pan_image", "pan_no", "udhayam_no", "udhayam_date", "udyam_principle_address", "udyam_principle_state",
                    "udyam_address_1", "udyam_address_2", "udyam_city", "udyam_pincode", "udham_pdf_url",
                    "trade_name", "selected_gst_number", "fuzzy_score", "legal_name_of_business", "gst_reg_date",
                    "constitution_of_business", "gst_state", "gst_address", "gst_status", "gst_city",
                    "gst_pincode", "address_1", "address_2", "landmark", "city", "state", "pincode",
                    "country", "rich_card_img", "gst_rich_card", "aadhar_validation", "acc_holder_name",
                    "bank_acc_no", "bank_ifsc_code", "bank_name", "fuzzy_logic_score", "otp",
                    "otp_validation", "aadhar_no_masked", "cheque_image", "po_rich_card",
                    "pdf_url", "api_pdf_url"
                };

                for (String field : excelFields) {
                    excelRow.createCell(colIndex++).setCellValue(nullSafe(row.get(field)));
                }
            }
            offset += BATCH_SIZE;
            rows = fetchBatch(formattedDate, offset, BATCH_SIZE);
        }

        try (FileOutputStream fos = new FileOutputStream(excelFile)) {
            workbook.write(fos);
        }
        workbook.close();

        logger.info("Excel created at: " + excelFile.getAbsolutePath());

        if (!hasData) {
            logger.warning("No data found for date: " + formattedDate);
        }

        try (FileInputStream fis = new FileInputStream(excelFile)) {
            Workbook readWorkbook = new XSSFWorkbook(fis);
            Sheet readSheet = readWorkbook.getSheetAt(0);

            Map<String, Integer> columnMap = new HashMap<>();
            Row headerRow = readSheet.getRow(0);
            for (Cell cell : headerRow) {
                columnMap.put(cell.getStringCellValue(), cell.getColumnIndex());
            }

            for (int i = 1; i <= readSheet.getLastRowNum(); i++) {
                Row row = readSheet.getRow(i);
                if (row == null) continue;

                String mobile = getCellValue(row.getCell(2));

                for (String field : FIELDS) {
                    String header = FIELD_TO_HEADER.get(field);
                    if (header == null) {
                        logger.warning("No header mapping for field: " + field);
                        continue;
                    }

                    Integer colIndex = columnMap.get(header);
                    if (colIndex == null) {
                        logger.warning("Column not found for header: " + header);
                        continue;
                    }

                    String url = getCellValue(row.getCell(colIndex));
                    if (!isValidUrl(url)) continue;

                    String extension = getExtensionFromUrl(url);
                    if (extension == null) {
                        logger.warning("Unsupported file extension in URL: " + url);
                        continue;
                    }

                    File targetDir = DOCUMENT_FIELDS.contains(field) ? docDir :
                                     IMAGE_FIELDS.contains(field) ? imageDir : null;

                    if (targetDir == null) {
                        logger.warning("Unknown field type, skipping: " + field);
                        continue;
                    }

                    if (DOCUMENT_FIELDS.contains(field) && !extension.equals(".pdf")) {
                        logger.warning("Expected PDF for field " + field + " but got: " + extension);
                    }

                    String fileName = mobile + "_" + header + extension;
                    File targetFile = new File(targetDir, fileName);

                    boolean success = downloadFile(url, targetFile);
                    if (success) {
                        downloadCounts.put(field, downloadCounts.getOrDefault(field, 0) + 1);
                    }
                }
            }

            readWorkbook.close();
        }

        logger.info("Report,Images,Documents - Download completed");
        for (Map.Entry<String, Integer> entry : downloadCounts.entrySet()) {
            logger.info(entry.getKey() + " => " + entry.getValue());
        }

        //sftpService.uploadFoldersToSftp(excelFile, imageDir, docDir, formattedDate);

        String yesterday = LocalDate.now().minusDays(2).toString();
        File yesterdayDir = new File(uploadDirectory, yesterday);
        boolean deleted = deleteDirectoryRecursively(yesterdayDir);
        if (deleted) {
            logger.info("Successfully deleted yesterday's folder: " + yesterdayDir.getAbsolutePath());
        } else {
            logger.warning("Failed to delete yesterday's folder: " + yesterdayDir.getAbsolutePath());
        }

        boolean excelGenerated = excelFile.exists();
        boolean documentsDownloaded = false;
        boolean imagesDownloaded = false;

        for (String field : DOCUMENT_FIELDS) {
            if (downloadCounts.getOrDefault(field, 0) > 0) documentsDownloaded = true;
        }
        for (String field : IMAGE_FIELDS) {
            if (downloadCounts.getOrDefault(field, 0) > 0) imagesDownloaded = true;
        }

        Map<String, Object> finalResponse = new HashMap<>();
        finalResponse.put("excel_generated", excelGenerated);
        finalResponse.put("documents_downloaded", documentsDownloaded);
        finalResponse.put("images_downloaded", imagesDownloaded);
        finalResponse.put("download_counts", downloadCounts);

        return finalResponse;
    }

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {
            "Completed Date", "Merchant Name", "Registered Phone Number", "Name of the Owner", "Name of the Store", "Email ID",
            "Number of Outlets", "Annual Turnover (in Rs)", "Store Manager Name", "Store Manager Phone Number",
            "PAN Image", "PAN No", "Udyam No", "Udyam Date", "Udyam Address", "Udyam State", "Udyam Address1",
            "Udyam Address2", "Udyam City", "Udyam Pincode", "Udyam PDF URL", "Trade Name", "GSTIN",
            "Fuzzy Logic Score (GST)", "Legal Name of Business", "GSTIN Registration Date",
            "Constitution of Business", "GSTIN State", "GSTIN Address", "GSTIN Status", "GSTIN City",
            "GSTIN Pincode", "Address Line 1", "Address Line 2", "Landmark", "City", "State", "Pincode",
            "Country", "Rich Card Image (Pan)", "GST Rich Card", "Aadhaar Validation", "Account Holder Name",
            "Bank Account No", "IFSC Code", "Bank Name", "Fuzzy Logic Score (Bank)", "OTP", "OTP Validation",
            "Masked Aadhaar No", "Cheque Image", "PO Rich Card", "Agreement PDF URL", "API PDF URL"
        };
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    private List<Map<String, Object>> fetchBatch(String date, int offset, int limit) {
        String query = "SELECT DISTINCT u.id,DATE_FORMAT(u.updated_at, '%d/%m/%Y') AS updated_at," +
                " m1.merchant_name, m1.reg_mobile_number, m1.form_owner_name, m1.store_name, m1.email_id, " +
                " m1.no_of_outlets, m1.annual_turn_over, m1.store_manager_name, m1.store_manager_phone_number, " +
                " m2.pan_image, m2.pan_no, m2.udhayam_no, m2.udhayam_date, m2.udyam_principle_address, m2.udyam_principle_state, " +
                " m2.udyam_address_1, m2.udyam_address_2, m2.udyam_city, m2.udyam_pincode, m2.udham_pdf_url, " +
                " m2.trade_name, m2.selected_gst_number, m2.fuzzy_score, m2.legal_name_of_business, m2.gst_reg_date, " +
                " m2.constitution_of_business, m2.gst_state, m2.gst_address, m2.gst_status, m2.gst_city, " +
                " m2.gst_pincode, m2.address_1, m2.address_2, m2.landmark, m2.city, m2.state, m2.pincode, " +
                " m2.country, m2.rich_card_img, m2.gst_rich_card, m3.aadhar_validation, m4.acc_holder_name, " +
                " m4.bank_acc_no, m4.bank_ifsc_code, m4.bank_name, m4.fuzzy_logic_score, m5.merchant_entered_otp, " +
                " m5.otp_validation, m3.aadhar_no_masked, m4.cheque_image, m4.rich_card_img AS po_rich_card, " +
                " m5.pdf_url, u.api_pdf_url " +
                " FROM usecase u " +
                " INNER JOIN milestone1 m1 ON m1.is_active = '1' AND m1.usecase_id = u.id " +
                " INNER JOIN milestone2 m2 ON m2.is_active = '1' AND m2.usecase_id = u.id " +
                " INNER JOIN milestone3 m3 ON m3.is_active = '1' AND m3.usecase_id = u.id " +
                " INNER JOIN milestone4 m4 ON m4.is_active = '1' AND m4.usecase_id = u.id " +
                " INNER JOIN milestone5 m5 ON m5.is_active = '1' AND m5.usecase_id = u.id " +
                " WHERE u.is_active = '1' AND u.current_milestone = '0' AND DATE(u.updated_at) = ? " +
                " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(query, date, limit, offset);
    }

    private String nullSafe(Object obj) {
        return obj == null ? "" : obj.toString().replaceAll(",", "").replaceAll("\n", "");
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.trim().isEmpty()
                && !url.equals("-")
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String getExtensionFromUrl(String url) {
        if (url == null) return null;
        String lowerUrl = url.toLowerCase().split("\\?")[0];

        if (lowerUrl.endsWith(".pdf")) return ".pdf";
        if (lowerUrl.endsWith(".jpg")) return ".jpg";
        if (lowerUrl.endsWith(".jpeg")) return ".jpeg";
        if (lowerUrl.endsWith(".png")) return ".png";
        if (lowerUrl.endsWith(".webp")) return ".webp";
        if (lowerUrl.endsWith(".bmp")) return ".bmp";
        if (lowerUrl.endsWith(".gif")) return ".gif";

        return null;
    }

    private boolean deleteDirectoryRecursively(File dir) {
        if (!dir.exists()) return false;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file);
                } else {
                    file.delete();
                }
            }
        }
        return dir.delete();
    }

    private boolean downloadFile(String urlStr, File destFile) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            int statusCode = connection.getResponseCode();

            if (statusCode == 200) {
                try (InputStream in = connection.getInputStream()) {
                    Files.copy(in, destFile.toPath());
                    return true;
                }
            } else {
                logger.warning("HTTP " + statusCode + ": " + urlStr);
                return false;
            }

        } catch (Exception e) {
            logger.warning("Exception downloading " + urlStr + ": " + e.getMessage());
            return false;
        }
    }
}
