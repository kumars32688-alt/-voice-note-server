package com.voicenote.app;

import java.util.HashMap;
import java.util.Map;

/**
 * KeywordMatcher.java - Simple keyword-based categorization
 *
 * HOW IT WORKS:
 * Instead of using complex AI/ML models, we use simple keyword matching
 * to categorize voice notes. This supports both Hindi and English words.
 *
 * CATEGORIES:
 * - Shopping   : grocery, buy, kharidna, saamaan, dukaan, market
 * - Reminder   : yaad, remind, kal, meeting, appointment, doctor
 * - Work       : office, project, kaam, deadline, boss, client, report
 * - Personal   : ghar, home, family, friend, dost, birthday, party
 * - Finance    : paisa, money, bank, payment, EMI, salary, kharcha
 * - Health     : doctor, dawai, medicine, exercise, gym, yoga, hospital
 * - Food       : khana, recipe, cook, restaurant, order, zomato, swiggy
 * - General    : anything that doesn't match above categories
 */
public class KeywordMatcher {

    // Map of category name -> array of keywords (Hindi + English)
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new HashMap<>();

    static {
        // Shopping related keywords (Hindi + English)
        CATEGORY_KEYWORDS.put("Shopping", new String[]{
            "buy", "shop", "shopping", "grocery", "market", "mall", "order",
            "kharidna", "khareedna", "saamaan", "saman", "dukaan", "bazaar",
            "amazon", "flipkart", "online", "price", "daam", "list"
        });

        // Reminder / Schedule keywords
        CATEGORY_KEYWORDS.put("Reminder", new String[]{
            "remind", "reminder", "remember", "yaad", "kal", "parso",
            "tomorrow", "meeting", "appointment", "schedule", "alarm",
            "bhoolna", "mat bhoolna", "time", "samay", "waqt", "jaldi"
        });

        // Work related keywords
        CATEGORY_KEYWORDS.put("Work", new String[]{
            "office", "work", "project", "kaam", "deadline", "boss",
            "client", "report", "email", "presentation", "task",
            "naukri", "company", "salary", "employee", "manager"
        });

        // Personal keywords
        CATEGORY_KEYWORDS.put("Personal", new String[]{
            "ghar", "home", "family", "friend", "dost", "birthday",
            "party", "vacation", "chutti", "shaadi", "wedding",
            "mummy", "papa", "bhai", "behen", "rishtedaar"
        });

        // Finance keywords
        CATEGORY_KEYWORDS.put("Finance", new String[]{
            "paisa", "paise", "money", "bank", "payment", "pay",
            "emi", "loan", "salary", "kharcha", "expense", "budget",
            "upi", "account", "transfer", "invest", "saving", "bachat"
        });

        // Health keywords
        CATEGORY_KEYWORDS.put("Health", new String[]{
            "doctor", "hospital", "dawai", "medicine", "health",
            "exercise", "gym", "yoga", "beemar", "sick", "fever",
            "bukhar", "dard", "pain", "checkup", "report", "test"
        });

        // Food keywords
        CATEGORY_KEYWORDS.put("Food", new String[]{
            "khana", "food", "recipe", "cook", "restaurant", "hotel",
            "order", "zomato", "swiggy", "breakfast", "lunch", "dinner",
            "nashta", "chai", "coffee", "sabzi", "roti", "dal", "chawal"
        });
    }

    /**
     * Detect category from the spoken text.
     * Converts text to lowercase and checks each word against keyword lists.
     *
     * @param text The transcribed voice text (can be Hindi, English, or mixed)
     * @return The detected category name (e.g., "Shopping", "Reminder", etc.)
     */
    public static String detectCategory(String text) {
        if (text == null || text.isEmpty()) {
            return "General";
        }

        // Convert to lowercase for matching
        String lowerText = text.toLowerCase().trim();

        // Count how many keywords match for each category
        String bestCategory = "General";
        int bestScore = 0;

        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            String category = entry.getKey();
            String[] keywords = entry.getValue();
            int score = 0;

            // Check each keyword
            for (String keyword : keywords) {
                if (lowerText.contains(keyword)) {
                    score++;
                }
            }

            // The category with the most keyword matches wins
            if (score > bestScore) {
                bestScore = score;
                bestCategory = category;
            }
        }

        return bestCategory;
    }

    /**
     * Generate a short title from the transcribed text.
     * Takes the first 5 words or 40 characters, whichever is shorter.
     *
     * @param text The full transcribed text
     * @return A short title string
     */
    public static String generateTitle(String text) {
        if (text == null || text.isEmpty()) {
            return "Untitled Note";
        }

        // Split into words and take first 5
        String[] words = text.trim().split("\\s+");
        StringBuilder title = new StringBuilder();

        int wordCount = Math.min(words.length, 5);
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) title.append(" ");
            title.append(words[i]);
        }

        // Add "..." if text was longer
        String result = title.toString();
        if (words.length > 5) {
            result += "...";
        }

        // Cap at 40 characters
        if (result.length() > 40) {
            result = result.substring(0, 37) + "...";
        }

        return result;
    }
}
