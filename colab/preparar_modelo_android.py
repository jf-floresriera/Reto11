# =========================================================================
# Notebook de Colab: fusionar adaptador LoRA + cuantizar a GGUF (y, opcional,
# exportar a ONNX int4 para comparar) — proyecto maize_llm, Reto 11
# Cada bloque "# Celda N" es una celda independiente de Colab.
# =========================================================================

# Celda 1 — Dependencias (mismas versiones que usó el proyecto, sección 9.1)
!pip install -q transformers==4.51.3 peft==0.15.2 accelerate torch==2.11.0 huggingface_hub

# Celda 2 — Montar Drive y autenticarse en Hugging Face
# Llama-3.2-1B-Instruct es un modelo con licencia: hay que haberla aceptado
# en huggingface.co y generar un token (Settings > Access Tokens).
# El token se lee del secreto de Colab 'HF_TOKEN' (panel de llave 🔑 a la
# izquierda del notebook), no de un prompt interactivo: así "Ejecutar todo"
# no se detiene a mitad de camino y el token no queda impreso en pantalla.
from google.colab import drive, userdata
from huggingface_hub import login, whoami

drive.mount('/content/drive')

try:
    token = userdata.get('HF_TOKEN')
except userdata.SecretNotFoundError:
    raise SystemExit(
        "No existe un secreto llamado 'HF_TOKEN'. Crea uno en el panel de "
        "llave (\U0001F511) a la izquierda del notebook, con ese nombre exacto."
    )
except userdata.NotebookAccessError:
    raise SystemExit(
        "El secreto 'HF_TOKEN' existe pero este notebook no tiene permiso para "
        "leerlo. Activa el interruptor de acceso junto al secreto en el panel "
        "de llave (\U0001F511)."
    )

token = (token or '').strip()
if not token.startswith('hf_') or ' ' in token or len(token) > 60:
    raise SystemExit('El secreto HF_TOKEN no parece válido: debe ser únicamente el texto que empieza por "hf_".')

login(token)
print('Sesión Hugging Face:', whoami()['name'])

# Celda 3 — Fusionar el adaptador LoRA (DAPT M2 + SFT, un solo adapter) con el base
# Ajusta ADAPTER_PATH si tu carpeta de Drive tiene otro nombre.
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel
import torch

BASE = "meta-llama/Llama-3.2-1B-Instruct"
ADAPTER_PATH = "/content/drive/MyDrive/maize_llm/runs/colab_sft_v1_llama32_1b/adapter"
MERGED_OUT   = "/content/drive/MyDrive/maize_llm/runs/merged_llama32_1b_maize"

tok = AutoTokenizer.from_pretrained(ADAPTER_PATH)
base_model = AutoModelForCausalLM.from_pretrained(BASE, torch_dtype=torch.bfloat16, device_map="cpu")
model = PeftModel.from_pretrained(base_model, ADAPTER_PATH)
merged = model.merge_and_unload()

merged.save_pretrained(MERGED_OUT, safe_serialization=True)
tok.save_pretrained(MERGED_OUT)
print("Modelo fusionado guardado en:", MERGED_OUT)

# Celda 4 — Clonar llama.cpp y convertir el modelo fusionado a GGUF (f16)
!git clone --depth 1 https://github.com/ggml-org/llama.cpp /content/llama.cpp
!pip install -q -r /content/llama.cpp/requirements.txt

GGUF_DIR = "/content/drive/MyDrive/maize_llm/runs/gguf"
!mkdir -p {GGUF_DIR}

!python /content/llama.cpp/convert_hf_to_gguf.py {MERGED_OUT} \
    --outfile {GGUF_DIR}/maize_llama32_1b_f16.gguf \
    --outtype f16

# Celda 5 — Compilar llama-quantize y cuantizar a Q4_K_M
# Q4_K_M es el punto de partida recomendado por llama.cpp para 1B en celulares
# de 6 GB+: calidad razonable con ~0.7-0.8 GB de archivo.
!cmake -S /content/llama.cpp -B /content/llama.cpp/build -DGGML_CUDA=OFF -DCMAKE_BUILD_TYPE=Release
!cmake --build /content/llama.cpp/build --target llama-quantize -j$(nproc)

!/content/llama.cpp/build/bin/llama-quantize \
    {GGUF_DIR}/maize_llama32_1b_f16.gguf \
    {GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf \
    Q4_K_M

!ls -lh {GGUF_DIR}

# Celda 6 — Verificación rápida en el propio Colab (antes de llevarlo al celular)
!cmake --build /content/llama.cpp/build --target llama-cli -j$(nproc)
!/content/llama.cpp/build/bin/llama-cli \
    -m {GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf \
    -c 2048 -n 200 \
    -p "<|start_header_id|>system<|end_header_id|>\n\nEres un asistente de sanidad vegetal para el cultivo de maíz en Colombia y Estados Unidos. Responde para un agricultor: lenguaje sencillo, frases cortas y pasos prácticos, sin tecnicismos. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el país de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n¿Cómo reconozco la mancha de asfalto en mi maíz?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"

# =========================================================================
# Celdas 7-8 (OPCIONAL) — Exportar también a ONNX int4 para comparar tamaño
# y velocidad contra el GGUF. No es necesario para el Reto 11; solo si
# quieres el dato comparativo.
# =========================================================================

# Celda 7 — Instalar onnxruntime-genai
!pip install -q onnxruntime-genai onnxruntime

# Celda 8 — Convertir el modelo fusionado (carpeta local, no el nombre del Hub)
ONNX_DIR = "/content/drive/MyDrive/maize_llm/runs/onnx/maize_llama32_1b_int4"
!mkdir -p /content/onnx_cache
!python -m onnxruntime_genai.models.builder \
    -i {MERGED_OUT} \
    -o {ONNX_DIR} \
    -p int4 \
    -e cpu \
    -c /content/onnx_cache

!ls -lh {ONNX_DIR}

# =========================================================================
# Celdas 9-12 — Evaluar el GGUF cuantizado contra el conjunto maestro SFT
# (sft_v1/test.jsonl) y guardar todo en Drive, antes de pasar a la app.
#
# OJO: no he visto el contenido real de sft_v1/test.jsonl, así que los
# nombres de campo (QUESTION_FIELD, LEVEL_FIELD, LANG_FIELD) de más abajo
# son una SUPOSICIÓN basada en la descripción del documento de contexto.
# Antes de correr la celda 11, ejecuta esto y pégame la salida para que
# ajuste las claves correctas:
#   !head -n 1 /content/drive/MyDrive/maize_llm_colab/data/datasets/sft_v1/test.jsonl
# =========================================================================

# Celda 9 — Levantar llama-server en segundo plano con el modelo cuantizado
import subprocess, time

SERVER_LOG = "/content/llama_server.log"
server_proc = subprocess.Popen(
    ["/content/llama.cpp/build/bin/llama-server",
     "-m", f"{GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf",
     "-c", "2048", "--host", "127.0.0.1", "--port", "8080"],
    stdout=open(SERVER_LOG, "w"), stderr=subprocess.STDOUT,
)
print("Arrancando servidor, PID:", server_proc.pid)

# Celda 10 — Esperar a que el servidor esté listo
import requests

listo = False
for _ in range(60):
    try:
        if requests.get("http://127.0.0.1:8080/health", timeout=2).status_code == 200:
            listo = True
            break
    except Exception:
        pass
    time.sleep(2)
print("Servidor listo" if listo else "El servidor no respondió a tiempo; revisa " + SERVER_LOG)

# Celda 11 — Recorrer sft_v1/test.jsonl, generar respuesta y chequear señales
# de riesgo (cifra sin respaldo, cita de fuente, idioma). AJUSTA las claves
# de campo según lo que te devuelva el !head de arriba.
import json, re, datetime

SFT_TEST_PATH = "/content/drive/MyDrive/maize_llm_colab/data/datasets/sft_v1/test.jsonl"  # <-- ajustar si aplica
RESULTS_PATH  = f"/content/drive/MyDrive/maize_llm/runs/eval_gguf_q4km_{datetime.date.today()}.jsonl"

QUESTION_FIELD = "question"   # <-- ajustar
LEVEL_FIELD    = "level"      # <-- ajustar: agricultor / tecnico / cientifico
LANG_FIELD     = "lang"       # <-- ajustar: es / en

SYSTEM_PROMPTS = {
    ("es", "agricultor"): "Eres un asistente de sanidad vegetal para el cultivo de maíz en Colombia y Estados Unidos. Responde para un agricultor: lenguaje sencillo, frases cortas y pasos prácticos, sin tecnicismos. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el país de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("es", "tecnico"): "Eres un asistente de sanidad vegetal para el cultivo de maíz en Colombia y Estados Unidos. Responde para un técnico o agrónomo: momentos de aplicación, umbrales, métodos y manejo integrado. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el país de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("es", "cientifico"): "Eres un asistente de sanidad vegetal para el cultivo de maíz en Colombia y Estados Unidos. Responde para un investigador: mecanismos, evidencia, condiciones y limitaciones de los estudios. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el país de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("en", "farmer"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a farmer: plain language, short sentences and practical steps, no jargon. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
    ("en", "technician"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a technician or agronomist: timing, thresholds, methods and integrated management. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
    ("en", "researcher"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a researcher: mechanisms, evidence, conditions and limitations of the studies. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
}

DOSE_RE = re.compile(r"\d+[\.,]?\d*\s*(L/ha|kg/ha|g/L|%|d[ií]as|days)", re.IGNORECASE)
CITA_RE = re.compile(r"(fuente|source)\s*:", re.IGNORECASE)

def preguntar(system, user, max_tokens=320):
    payload = {
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "temperature": 0.0,
        "max_tokens": max_tokens,
        "repeat_penalty": 1.1,
    }
    r = requests.post("http://127.0.0.1:8080/v1/chat/completions", json=payload, timeout=180)
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]

with open(SFT_TEST_PATH, encoding="utf-8") as f:
    items = [json.loads(linea) for linea in f]
print(f"{len(items)} conversaciones de prueba cargadas")

resultados = []
for i, item in enumerate(items):
    lang = item[LANG_FIELD]
    level = item[LEVEL_FIELD]
    pregunta = item[QUESTION_FIELD]
    system = SYSTEM_PROMPTS.get((lang, level))
    if system is None:
        registro = {"idx": i, "error": f"combinación lang/level sin system prompt: {lang}/{level}"}
    else:
        try:
            respuesta = preguntar(system, pregunta)
            registro = {
                "idx": i,
                "lang": lang,
                "level": level,
                "pregunta": pregunta,
                "respuesta": respuesta,
                "cita_fuente": bool(CITA_RE.search(respuesta)),
                "cifra_posible_sin_respaldo": bool(DOSE_RE.search(respuesta)),
            }
        except Exception as e:
            registro = {"idx": i, "error": str(e)}
    resultados.append(registro)

    # Guardado incremental: si se corta la sesión, no se pierde lo ya hecho.
    if i % 20 == 0 or i == len(items) - 1:
        with open(RESULTS_PATH, "w", encoding="utf-8") as out:
            for r_ in resultados:
                out.write(json.dumps(r_, ensure_ascii=False) + "\n")
        print(f"{i + 1}/{len(items)} procesadas — guardado en {RESULTS_PATH}")

print("Evaluación terminada. Resultados en:", RESULTS_PATH)

# Celda 12 — Resumen rápido y cierre del servidor
import pandas as pd

df = pd.DataFrame([r for r in resultados if "error" not in r])
if len(df):
    print(df.groupby(["lang", "level"])[["cita_fuente", "cifra_posible_sin_respaldo"]].mean())
else:
    print("No hay resultados válidos para resumir; revisa los errores en", RESULTS_PATH)

server_proc.terminate()
print("Servidor detenido.")
