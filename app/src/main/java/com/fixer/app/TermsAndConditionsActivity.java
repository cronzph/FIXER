package com.fixer.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.InputStream;
import java.util.List;

public class TermsAndConditionsActivity extends AppCompatActivity {

    private static final String TAG = "TermsActivity";

    private WebView webView;
    private ProgressBar progressBar;
    private MaterialButton closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_and_conditions);

        // Initialize views
        initializeViews();

        // Setup toolbar
        setupToolbar();

        // Load terms document
        loadTermsDocument();
    }

    private void initializeViews() {
        webView = findViewById(R.id.termsWebView);
        progressBar = findViewById(R.id.termsProgressBar);
        closeButton = findViewById(R.id.closeButton);

        closeButton.setOnClickListener(v -> finish());
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Terms and Conditions");
        }
    }

    private void loadTermsDocument() {
        progressBar.setVisibility(View.VISIBLE);

        try {
            // Read the .doc or .docx file from assets folder
            InputStream inputStream = getAssets().open("terms_and_conditions.docx");

            // Parse DOCX file using Apache POI
            XWPFDocument document = new XWPFDocument(inputStream);
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            // Convert to HTML
            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<html><head>");
            htmlContent.append("<style>");
            htmlContent.append("body { font-family: Arial, sans-serif; padding: 16px; line-height: 1.6; color: #333; }");
            htmlContent.append("h1, h2, h3 { color: #D32F2F; margin-top: 20px; }");
            htmlContent.append("p { margin: 10px 0; text-align: justify; }");
            htmlContent.append("ul { margin: 10px 0; padding-left: 20px; }");
            htmlContent.append("li { margin: 5px 0; }");
            htmlContent.append("</style>");
            htmlContent.append("</head><body>");

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    // Check if it's a heading based on style or formatting
                    String style = paragraph.getStyle();
                    if (style != null && (style.contains("Heading") || style.contains("Title"))) {
                        htmlContent.append("<h2>").append(text).append("</h2>");
                    } else {
                        htmlContent.append("<p>").append(text).append("</p>");
                    }
                }
            }

            htmlContent.append("</body></html>");

            // Load HTML content into WebView
            webView.loadDataWithBaseURL(null, htmlContent.toString(), "text/html", "UTF-8", null);

            document.close();
            inputStream.close();

            progressBar.setVisibility(View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "Error loading terms document", e);
            progressBar.setVisibility(View.GONE);

            // Fallback: Load from raw text or show error
            loadFallbackTerms();
        }
    }

    private void loadFallbackTerms() {
        String fallbackHtml = "<html><head>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; padding: 16px; line-height: 1.6; color: #333; }" +
                "h2 { color: #D32F2F; margin-top: 20px; }" +
                "p { margin: 10px 0; text-align: justify; }" +
                "</style>" +
                "</head><body>" +
                "<h2>Terms and Conditions</h2>" +
                "<p>Please contact the administrator to view the complete Terms and Conditions document.</p>" +
                "<p>By using the F.I.X.E.R system, you agree to:</p>" +
                "<ul>" +
                "<li>Provide accurate information</li>" +
                "<li>Use the system responsibly</li>" +
                "<li>Maintain account confidentiality</li>" +
                "<li>Follow institutional policies</li>" +
                "</ul>" +
                "</body></html>";

        webView.loadDataWithBaseURL(null, fallbackHtml, "text/html", "UTF-8", null);
        Toast.makeText(this, "Unable to load document file. Showing summary.", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}