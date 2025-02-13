package com.rk.mutator_engine;

public interface EngineAPI {
    void showToast(String text);
    String getEditorText();

    /**
     * Sets the text in the editor.
     * @param text the text you wan to set
     */
    void setEditorText(String text);
    String getEditorTextFromPath(String path);

    /**
     * Makes an HTTP request.
     *
     * @param url     the URL to make the request to
     * @param options a JSON payload as a string
     * Example:
     * <pre>
     * {@code
     * const url = "https://example.com";
     * const options = JSON.stringify({
     *     method: "GET",
     *     headers: {
     *         "Accept": "application/json"
     *     }
     * });
     * const response = http(url, options);
     * }
     * </pre>
     */
    String http(String url, String options);
    void showDialog(String title,String content);
    void exit();
    void sleep(double millis);
}
