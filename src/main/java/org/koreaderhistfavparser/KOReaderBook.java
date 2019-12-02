package org.koreaderhistfavparser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * The class for a book with properties read from KOReader sdr files.
 */
public class KOReaderBook {
    private String filePath;
    private Boolean finished = false;
    private Double percentFinished;     // progress in range [0, 1]
    private Long lastRead = (long) 0;   // time in Unix time
    private Integer pages;
    private String title;
    private String[] authors;
    private String[] keywords;
    private String language;
    private String series;
    private String sdrFilePath;
    private Long sdrFileLastModified = (long) 0;
    private JSONObject sdrJson;
    // %t: title, %a: first author, %p: progress in percent, %s: series, %l: language
    private String stringFormat = "[%a: ]%t[ (%p%)]";

    /**
     * Constructs a new KOReaderBook with the specified file path.
     *
     * @param filePath the file path
     */
    public KOReaderBook(String filePath) {
        this.filePath = filePath;
        sdrFilePath = sdrFilePath(filePath);
    }

    /**
     * Compares this with another object.
     *
     * @param obj to compare with
     * @return true if objects or if file paths are identical, otherwise false
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || !obj.getClass().equals(getClass()))
            return false;
        KOReaderBook book = (KOReaderBook) obj;
        return filePath.equals(book.filePath);
    }

    @Override
    public int hashCode() {
        return filePath.hashCode();
    }

    /**
     * Returns the formatted string according to the specified string format with the format
     * classifiers
     * <ul>
     *     <li><code>%t: title</code>,</li>
     *     <li><code>%a: first author</code>,</li>
     *     <li><code>%p: progress in percent</code>,</li>
     *     <li><code>%s: series</code> and </li>
     *     <li><code>%l: language.</code>
     * </ul>
     * Optional classifiers are set by square brackets. The string format can be set by
     * {@link #setStringFormat} and is returned by {@link #getStringFormat}. It defaults to
     * <code>[%a: ]%t[ (%p%)]</code>.
     *
     * @return formatted string
     */
    @Override
    public String toString() {
        String output = stringFormat;
        if (output.contains("%t")) {
            getTitle();
            if (title != null)
                output = output.replace("%t", title);
        }
        if (output.contains("%a")) {
            getAuthors();
            if (authors != null && authors.length != 0 && authors[0] != null)
                output = output.replace("%a", authors[0]);
        }
        if (output.contains("%p")) {
            getPercentFinished();
            if (percentFinished != null)
                output = output.replace("%p", String.valueOf(Math.round(100 * percentFinished)));
        }
        if (output.contains("%s")) {
            getSeries();
            if (series != null)
                output = output.replace("%s", series);
        }
        if (output.contains("%l")) {
            getLanguage();
            if (language != null)
                output = output.replace("%l", language);
        }
        String r1 = "\\[[^\\[\\]]*%[tapsl][^\\[\\]]*\\]";   // matches e.g. [%a: ], [ (%p%)]
        String r2 = "\\[([^\\[\\]]*)\\]";                   // e.g. [XY: ], [ (24%)], not [*[*]*]
        while (output.matches(".*" + r2 + ".*")) {
            while (output.matches(".*" + r1 + ".*")) {
                output = output.replaceAll(r1, "");
            }
            output = output.replaceAll(r2, "$1");
        }

        // if not optional by [] and not replaced above, replace by following terms
        output = output.replaceAll("%t", "(no title)");
        output = output.replaceAll("%a", "(no author)");
        output = output.replaceAll("%p", "(no progress)");
        output = output.replaceAll("%s", "(no series)");
        output = output.replaceAll("%l", "(no language)");
        return output;
    }

    private  <T> Boolean propertyOutdated(T property) {
        Boolean sdrFileModified = sdrFileModified();
        if (property != null && !sdrFileModified)
            return false;
        if (sdrFileModified)
            readSdr();
        return true;
    }

    /**
     * Returns the file path.
     *
     * @return the file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Returns true if book is finished, otherwise or if no sdr file is found false.
     *
     * @return true if book is finished, otherwise false
     */
    public Boolean getFinished() {
        if (propertyOutdated(finished))
            try {
                String finishedString = sdrJson.getJSONObject("summary").getString("status");
                finished = (finishedString.equals("complete"));
            } catch (JSONException e) {}
        return finished;
    }

    /**
     * Sets the finished flag.
     *
     * @param finished true if book is to be set finished, otherwise false
     * @return true if successfully changed finished state, otherwise false
     */
    public Boolean setFinished(Boolean finished) {
        if (finished)
            return setFinished();
        else
            return setReading();
    }

    /**
     * Sets the finished flag to true.
     *
     * @return true if successfully changed finished state, otherwise false
     */
    public Boolean setFinished() {
        if (finished || sdrJson == null)
            return false;
        try {
            JSONObject summaryJson = sdrJson.getJSONObject("summary");
            summaryJson.put("status", "complete");
            finished = writeSdr();
            return finished;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Sets the finished flag to false.
     *
     * @return true if successfully changed finished state, otherwise false
     */
    public Boolean setReading() {
        if (!finished || sdrJson == null)
            return false;
        try {
            JSONObject summaryJson = sdrJson.getJSONObject("summary");
            summaryJson.put("status", "reading");
            finished = writeSdr();
            return finished;
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * Returns the progress in the range [0,1].
     *
     * @return the percent finished; null if not extractable from sdr file
     */
    public Double getPercentFinished() {
        if (propertyOutdated(percentFinished))
            try {
                percentFinished = sdrJson.getDouble("percent_finished");
            } catch (JSONException e) {}
        return percentFinished;
    }

    /**
     * Returns the time of last reading in Unix time format.
     *
     * @return the time of last reading
     */
    public Long getLastRead() {
        return lastRead;
    }

    /**
     * Sets the time of last reading in Unix time format.
     *
     * @param lastRead the time of last reading
     */
    public void setLastRead(Long lastRead) {
        this.lastRead = lastRead;
    }

    /**
     * Returns the number of pages.
     *
     * @return the number of pages; null if not extractable from sdr file
     */
    public Integer getPages() {
        if (propertyOutdated(pages))
            try {
                pages = sdrJson.getJSONObject("stats").getInt("pages");
            } catch (JSONException e) {}
        return pages;
    }

    /**
     * Returns the title.
     *
     * @return the title; null if not extractable from sdr file
     */
    public String getTitle() {
        if (propertyOutdated(title))
            try {
                title = sdrJson.getJSONObject("stats").getString("title");
            } catch (JSONException e) {}
        return title;
    }

    /**
     * Returns the array of authors.
     *
     * @return the array of authors; null if not extractable from sdr file
     */
    public String[] getAuthors() {
        if (propertyOutdated(authors))
            try {
                String authorsString = sdrJson.getJSONObject("stats").getString("authors");
                authors = authorsString.split(";;;;");
            } catch (JSONException e) {}
        return authors;
    }

    /**
     * Returns the array of keywords.
     *
     * @return the array of keywords; null if not extractable from sdr file
     */
    public String[] getKeywords() {
        if (propertyOutdated(keywords))
            try {
                String keywordsString = sdrJson.getJSONObject("stats").getString("keywords");
                keywords = keywordsString.split(";;;;");
            } catch (JSONException e) {}
        return keywords;
    }

    /**
     * Returns the language.
     *
     * @return the language; null if not extractable from sdr file
     */
    public String getLanguage() {
        if (propertyOutdated(keywords))
            try {
                language = sdrJson.getJSONObject("stats").getString("language");
            } catch (JSONException e) {}
        return language;
    }

    /**
     * Returns the series.
     *
     * @return the series; null if not extractable from sdr file
     */
    public String getSeries() {
        if (propertyOutdated(series))
            try {
                series = sdrJson.getJSONObject("stats").getString("series");
            } catch (JSONException e) {}
        return series;
    }

    /**
     * Returns the string format for the string representation with the format classifiers
     * <ul>
     *     <li><code>%t: title</code>,</li>
     *     <li><code>%a: first author</code>,</li>
     *     <li><code>%p: progress in percent</code>,</li>
     *     <li><code>%s: series</code> and </li>
     *     <li><code>%l: language.</code>
     * </ul>
     * Optional classifiers are set by square brackets.
     * Defaults to <code>[%a: ]%t[ (%p%)]</code>.
     *
     * @return the string format
     */
    public String getStringFormat() {
        return stringFormat;
    }

    /**
     * Sets the string format for the string representation with the format classifiers
     * <ul>
     *     <li><code>%t: title</code>,</li>
     *     <li><code>%a: first author</code>,</li>
     *     <li><code>%p: progress in percent</code>,</li>
     *     <li><code>%s: series</code> and </li>
     *     <li><code>%l: language.</code>
     * </ul>
     * Optional classifiers are set by square brackets.
     * Defaults to <code>[%a: ]%t[ (%p%)]</code>.
     *
     * @param stringFormat the string format
     */
    public void setStringFormat(String stringFormat) {
        this.stringFormat = stringFormat;
    }

    private String sdrFilePath(String filePath) {
        String filePathWithoutExt = filePath.substring(0, filePath.lastIndexOf("."));
        String filePathExt = filePath.substring(filePath.lastIndexOf("."));
        return filePathWithoutExt + ".sdr/metadata" + filePathExt + ".lua";
    }

    /**
     * Returns true if sdr file has been modified since last access, otherwise false.
     *
     * @return true if sdr file has been modified, otherwise false
     */
    private Boolean sdrFileModified() {
        return sdrFileLastModified < new File(sdrFilePath).lastModified();
    }

    /**
     * Read the sdr file and convert internally to JSON object.
     *
     * @return true, if reading and conversion successfully, otherwise false
     */
    private Boolean readSdr() {
        sdrFileLastModified = new File(sdrFilePath).lastModified();
        sdrJson = KOReaderLuaReadWrite.readLuaFile(sdrFilePath);
        return (sdrJson != null);
    }

    /**
     * Converts the internal JSON object and writes the output to the sdr file.
     *
     * @return true, if conversion and writing successfully, otherwise false
     */
    private Boolean writeSdr() {
        return KOReaderLuaReadWrite.writeLuaFile(sdrFilePath, sdrJson);
    }
}
