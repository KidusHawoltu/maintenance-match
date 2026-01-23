package com.maintenance_match.notification.service;

import java.util.Map;

public interface EmailService {
    /**
     * Sends an HTML email using a Thymeleaf template.
     * @param to Recipient email address
     * @param subject Email subject line
     * @param templateName The name of the HTML file in resources/templates (without .html)
     * @param variables Map of variables to replace in the template
     */
    void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables);
}
