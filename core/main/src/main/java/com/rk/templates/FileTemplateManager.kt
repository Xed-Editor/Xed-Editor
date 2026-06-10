package com.rk.templates

data class FileTemplate(
    val name: String,
    val extension: String,
    val mimeType: String,
    val content: String,
    val category: String,
)

object FileTemplateManager {
    val templates: List<FileTemplate> =
        listOf(
            // Plain Text
            FileTemplate("Plain Text", "txt", "text/plain", "", "Text"),
            FileTemplate("Markdown", "md", "text/markdown", "# Title\n\n", "Text"),
            FileTemplate("CSV", "csv", "text/csv", "column1,column2,column3\n", "Text"),

            // Web
            FileTemplate(
                "HTML5",
                "html",
                "text/html",
                """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
</head>
<body>
    <h1>Hello, World!</h1>
</body>
</html>""",
                "Web",
            ),
            FileTemplate("CSS", "css", "text/css", "body {\n    margin: 0;\n    padding: 0;\n}\n", "Web"),
            FileTemplate(
                "JavaScript",
                "js",
                "application/javascript",
                "'use strict';\n\nconsole.log('Hello, World!');\n",
                "Web",
            ),
            FileTemplate(
                "TypeScript",
                "ts",
                "application/typescript",
                "const greeting: string = 'Hello, World!';\nconsole.log(greeting);\n",
                "Web",
            ),
            FileTemplate(
                "JSX",
                "jsx",
                "text/javascript",
                "import React from 'react';\n\nexport default function App() {\n    return <div>Hello, World!</div>;\n}\n",
                "Web",
            ),
            FileTemplate(
                "TSX",
                "tsx",
                "text/typescript",
                "import React from 'react';\n\nexport default function App(): React.JSX.Element {\n    return <div>Hello, World!</div>;\n}\n",
                "Web",
            ),
            FileTemplate(
                "JSON",
                "json",
                "application/json",
                "{\n    \"key\": \"value\"\n}\n",
                "Web",
            ),
            FileTemplate(
                "YAML",
                "yaml",
                "text/yaml",
                "# Configuration\nkey: value\n",
                "Web",
            ),
            FileTemplate(
                "XML",
                "xml",
                "application/xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n</root>\n",
                "Web",
            ),

            // Systems
            FileTemplate(
                "C",
                "c",
                "text/x-c",
                """#include <stdio.h>

int main() {
    printf("Hello, World!\n");
    return 0;
}
""",
                "Systems",
            ),
            FileTemplate(
                "C++",
                "cpp",
                "text/x-c++",
                """#include <iostream>

int main() {
    std::cout << "Hello, World!" << std::endl;
    return 0;
}
""",
                "Systems",
            ),
            FileTemplate(
                "Rust",
                "rs",
                "text/x-rust",
                "fn main() {\n    println!(\"Hello, World!\");\n}\n",
                "Systems",
            ),
            FileTemplate("Shell Script", "sh", "text/x-shellscript", "#!/bin/bash\n\necho \"Hello, World!\"\n", "Systems"),
            FileTemplate(
                "Makefile",
                "makefile",
                "text/x-makefile",
                "CC = gcc\nCFLAGS = -Wall -Wextra\n\nall: main\n\nmain: main.o\n\t$(CC) $(CFLAGS) -o main main.o\n\nclean:\n\trm -f main *.o\n",
                "Systems",
            ),

            // JVM
            FileTemplate(
                "Java",
                "java",
                "text/x-java",
                """public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
""",
                "JVM",
            ),
            FileTemplate(
                "Kotlin",
                "kt",
                "text/x-kotlin",
                "fun main() {\n    println(\"Hello, World!\")\n}\n",
                "JVM",
            ),
            FileTemplate(
                "Kotlin Script",
                "kts",
                "text/x-kotlin",
                "println(\"Hello, World!\")\n",
                "JVM",
            ),
            FileTemplate(
                "Scala",
                "scala",
                "text/x-scala",
                "object Main extends App {\n    println(\"Hello, World!\")\n}\n",
                "JVM",
            ),
            FileTemplate(
                "Groovy",
                "groovy",
                "text/x-groovy",
                "println 'Hello, World!'\n",
                "JVM",
            ),

            // Python
            FileTemplate(
                "Python",
                "py",
                "text/x-python",
                "#!/usr/bin/env python3\n\ndef main():\n    print(\"Hello, World!\")\n\nif __name__ == \"__main__\":\n    main()\n",
                "Python",
            ),
            FileTemplate(
                "Python Script",
                "py",
                "text/x-python",
                "#!/usr/bin/env python3\n\nprint(\"Hello, World!\")\n",
                "Python",
            ),

            // Mobile
            FileTemplate(
                "Swift",
                "swift",
                "text/x-swift",
                "import Foundation\n\nprint(\"Hello, World!\")\n",
                "Mobile",
            ),
            FileTemplate(
                "Dart",
                "dart",
                "text/x-dart",
                "void main() {\n    print('Hello, World!');\n}\n",
                "Mobile",
            ),

            // Config
            FileTemplate(
                "Dockerfile",
                "Dockerfile",
                "text/x-dockerfile",
                "FROM node:18-alpine\n\nWORKDIR /app\n\nCOPY . .\n\nRUN npm install\n\nCMD [\"npm\", \"start\"]\n",
                "Config",
            ),
            FileTemplate(
                "Gitignore",
                "gitignore",
                "text/plain",
                "# Build\ndist/\nbuild/\n\n# Dependencies\nnode_modules/\n\n# IDE\n.idea/\n.vscode/\n*.swp\n\n# OS\n.DS_Store\nThumbs.db\n",
                "Config",
            ),
            FileTemplate(
                "EditorConfig",
                "editorconfig",
                "text/plain",
                "root = true\n\n[*]\nindent_style = space\nindent_size = 4\nend_of_line = lf\ncharset = utf-8\ntrim_trailing_whitespace = true\ninsert_final_newline = true\n",
                "Config",
            ),
            FileTemplate(
                "TOML",
                "toml",
                "text/plain",
                "[package]\nname = \"my-project\"\nversion = \"0.1.0\"\nedition = \"2021\"\n",
                "Config",
            ),

            // Data
            FileTemplate("SQL", "sql", "text/x-sql", "-- Create table\nCREATE TABLE users (\n    id INTEGER PRIMARY KEY,\n    name TEXT NOT NULL,\n    email TEXT UNIQUE\n);\n", "Data"),
            FileTemplate("GraphQL", "graphql", "text/graphql", "type Query {\n    hello: String\n}\n", "Data"),
        )

    fun getByCategory(): Map<String, List<FileTemplate>> = templates.groupBy { it.category }

    fun getByExtension(extension: String): FileTemplate? = templates.find { it.extension == extension }

    fun getCategories(): List<String> = templates.map { it.category }.distinct()
}
