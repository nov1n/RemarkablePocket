package nl.carosi.remarkablepocket.model;


public final record Article(String id, String url, String title) {
    public static Article of(String id, String url, String title) {
        return new Article(id, url, sanitize(title));
    }

    private static String sanitize(String title) {
        return title
                .replaceAll("[‘’\"]", "'")
                .replaceAll(":", " -")
                .replaceAll("[/?<>*.|\\\\]", " ")
                .replaceAll(" +", " ").strip();
    }
}
