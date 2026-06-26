import sqlite3
import os

# Путь к базе данных assets. Измените под своё окружение.
db_path = os.path.join(
    os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'brp_assistant.db'
)
db_path = os.path.normpath(db_path)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()


def fix_table(table_name, create_sql, columns):
    print(f"Fixing {table_name}...")
    try:
        cursor.execute(f"CREATE TABLE {table_name}_new {create_sql}")
        cols_str = ", ".join(columns)
        cursor.execute(f"INSERT INTO {table_name}_new ({cols_str}) SELECT {cols_str} FROM {table_name}")
        cursor.execute(f"DROP TABLE {table_name}")
        cursor.execute(f"ALTER TABLE {table_name}_new RENAME TO {table_name}")
    except Exception as e:
        print(f"Error fixing {table_name}: {e}")


try:
    cursor.execute("PRAGMA foreign_keys=OFF")
    cursor.execute("BEGIN TRANSACTION")

    # Fix brp_models
    fix_table(
        "brp_models",
        "(id TEXT PRIMARY KEY NOT NULL, brand TEXT NOT NULL, category TEXT NOT NULL, "
        "subcategory TEXT NOT NULL, model_year INTEGER NOT NULL DEFAULT 2026, "
        "modelName TEXT NOT NULL, platform TEXT, engineName TEXT, engineType TEXT, "
        "displacementCc REAL, horsepower INTEGER, transmission TEXT, driveType TEXT, "
        "keyFeatures TEXT, whatNew TEXT, colors TEXT, msrpUsd REAL, "
        "isElectric INTEGER NOT NULL DEFAULT 0, description TEXT)",
        ["id", "brand", "category", "subcategory", "model_year", "modelName",
         "platform", "engineName", "engineType", "displacementCc", "horsepower",
         "transmission", "driveType", "keyFeatures", "whatNew", "colors",
         "msrpUsd", "isElectric", "description"]
    )
    cursor.execute("CREATE INDEX idx_models_brand ON brp_models(brand)")
    cursor.execute("CREATE INDEX idx_models_category ON brp_models(category)")
    cursor.execute("CREATE INDEX idx_models_subcategory ON brp_models(subcategory)")

    # Fix brp_accessories
    fix_table(
        "brp_accessories",
        "(id TEXT PRIMARY KEY NOT NULL, sku TEXT NOT NULL, brand TEXT NOT NULL, "
        "category TEXT NOT NULL, subcategory TEXT, name TEXT NOT NULL, "
        "description TEXT NOT NULL, compatiblePlatforms TEXT, compatibleModels TEXT, "
        "msrpUsd REAL, isNew2026 INTEGER NOT NULL DEFAULT 0, "
        "requiresProfessionalInstall INTEGER NOT NULL DEFAULT 0, "
        "requiresParts TEXT, tags TEXT)",
        ["id", "sku", "brand", "category", "subcategory", "name", "description",
         "compatiblePlatforms", "compatibleModels", "msrpUsd", "isNew2026",
         "requiresProfessionalInstall", "requiresParts", "tags"]
    )
    cursor.execute("CREATE INDEX index_brp_accessories_sku ON brp_accessories(sku)")
    cursor.execute("CREATE INDEX idx_acc_brand ON brp_accessories(brand)")
    cursor.execute("CREATE INDEX idx_acc_category ON brp_accessories(category)")

    # Fix knowledge_cards
    fix_table(
        "knowledge_cards",
        "(id TEXT PRIMARY KEY NOT NULL, equipmentType TEXT NOT NULL, "
        "brand TEXT NOT NULL, modelFamily TEXT, node TEXT NOT NULL, "
        "symptom TEXT NOT NULL, riskLevel TEXT NOT NULL, "
        "requiresEvacuation INTEGER NOT NULL DEFAULT 0, compatibleModels TEXT, "
        "causes TEXT NOT NULL DEFAULT '[]', steps TEXT NOT NULL DEFAULT '[]', "
        "canDo TEXT NOT NULL DEFAULT '[]', mustNotDo TEXT NOT NULL DEFAULT '[]', "
        "stopConditions TEXT NOT NULL DEFAULT '[]', fullText TEXT NOT NULL DEFAULT '')",
        ["id", "equipmentType", "brand", "modelFamily", "node", "symptom",
         "riskLevel", "requiresEvacuation", "compatibleModels", "causes",
         "steps", "canDo", "mustNotDo", "stopConditions", "fullText"]
    )

    # Fix fault_codes
    fix_table(
        "fault_codes",
        "(code TEXT PRIMARY KEY NOT NULL, brand TEXT NOT NULL, "
        "description TEXT NOT NULL, possibleCauses TEXT, solution TEXT)",
        ["code", "brand", "description", "possibleCauses", "solution"]
    )

    cursor.execute("COMMIT")
    print("All tables fixed successfully!")
except Exception as e:
    cursor.execute("ROLLBACK")
    print(f"Global error: {e}")
finally:
    conn.close()
