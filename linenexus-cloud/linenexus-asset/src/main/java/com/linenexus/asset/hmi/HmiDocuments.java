package com.linenexus.asset.hmi;

final class HmiDocuments {

    private HmiDocuments() {
    }

    static String emptyDocumentJson() {
        return """
                {
                  "schemaVersion": 1,
                  "canvas": { "width": 1920, "height": 1080, "background": "#1a1d24" },
                  "layers": [
                    { "id": "layer-default", "name": "默认", "visible": true, "locked": false }
                  ],
                  "elements": [],
                  "meta": { "grid": 10, "snap": false }
                }
                """;
    }
}
