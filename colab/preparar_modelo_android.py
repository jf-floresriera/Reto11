# =========================================================================
# Notebook de Colab — proyecto maize_llm, Reto 11
# Fusionar adaptador LoRA + cuantizar a GGUF + evaluar el modelo cuantizado.
# Cada bloque "# Celda N" es una celda independiente de Colab. Copia cada
# celda completa (no a mano encima de una vieja) para evitar errores de
# copiado y pegado.
# =========================================================================

# Celda 1 — Dependencias
!pip install -q transformers==4.51.3 peft==0.15.2 accelerate torch==2.11.0 huggingface_hub

# =========================================================================
# Celda 2 — Montar Drive y autenticarse en Hugging Face
# Llama-3.2-1B-Instruct es un modelo con licencia: hay que haberla aceptado
# en huggingface.co y generar un token (Settings > Access Tokens).
# El token se lee del secreto de Colab 'HF_TOKEN' (panel de llave a la
# izquierda del notebook), no de un prompt interactivo.
# =========================================================================
from google.colab import drive, userdata
from huggingface_hub import login, whoami

drive.mount('/content/drive')

try:
    token = userdata.get('HF_TOKEN')
except userdata.SecretNotFoundError:
    raise SystemExit(
        "No existe un secreto llamado 'HF_TOKEN'. Crealo en el panel de "
        "llave a la izquierda del notebook, con ese nombre exacto."
    )
except userdata.NotebookAccessError:
    raise SystemExit(
        "El secreto 'HF_TOKEN' existe pero este notebook no tiene permiso "
        "para leerlo. Activa el interruptor de acceso junto al secreto."
    )

token = (token or '').strip()
if not token.startswith('hf_') or ' ' in token or len(token) > 60:
    raise SystemExit('El secreto HF_TOKEN no parece valido: debe ser unicamente el texto que empieza por "hf_".')

login(token)
print('Sesion Hugging Face:', whoami()['name'])

# =========================================================================
# Celda 3 — Fusionar el adaptador LoRA (DAPT M2 + SFT, un solo adapter) con el base
# =========================================================================
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

# =========================================================================
# Celda 4 — Clonar llama.cpp y convertir el modelo fusionado a GGUF (f16)
# =========================================================================
!git clone --depth 1 https://github.com/ggml-org/llama.cpp /content/llama.cpp
!pip install -q -r /content/llama.cpp/requirements.txt

GGUF_DIR = "/content/drive/MyDrive/maize_llm/runs/gguf"
!mkdir -p {GGUF_DIR}

!python /content/llama.cpp/convert_hf_to_gguf.py {MERGED_OUT} \
    --outfile {GGUF_DIR}/maize_llama32_1b_f16.gguf \
    --outtype f16

# =========================================================================
# Celda 5 — Compilar llama-quantize y cuantizar a Q4_K_M
# =========================================================================
!cmake -S /content/llama.cpp -B /content/llama.cpp/build -DGGML_CUDA=OFF -DCMAKE_BUILD_TYPE=Release
!cmake --build /content/llama.cpp/build --target llama-quantize -j$(nproc)

!/content/llama.cpp/build/bin/llama-quantize \
    {GGUF_DIR}/maize_llama32_1b_f16.gguf \
    {GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf \
    Q4_K_M

!ls -lh {GGUF_DIR}

# =========================================================================
# Celda 6 — Verificacion rapida en el propio Colab (antes de llevarlo al celular)
# =========================================================================
!cmake --build /content/llama.cpp/build --target llama-cli -j$(nproc)
!/content/llama.cpp/build/bin/llama-cli \
    -m {GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf \
    -c 2048 -n 200 \
    -p "<|start_header_id|>system<|end_header_id|>\n\nEres un asistente de sanidad vegetal para el cultivo de maiz en Colombia y Estados Unidos. Responde para un agricultor: lenguaje sencillo, frases cortas y pasos practicos, sin tecnicismos. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el pais de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n¿Como reconozco la mancha de asfalto en mi maiz?<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"

# =========================================================================
# Celdas 7-8 (OPCIONAL) — Exportar tambien a ONNX int4, solo para comparar.
# No es necesario para el Reto 11.
# =========================================================================
!pip install -q onnxruntime-genai onnxruntime

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
# Celda 9 — Copiar el GGUF a disco LOCAL de Colab (Drive montado por FUSE es
# lento para leer archivos grandes la primera vez) y levantar llama-server.
# =========================================================================
import os, shutil, subprocess, time

# Si el runtime se reinicio y perdiste las variables de las celdas 1-8, usa la ruta por defecto.
GGUF_DIR = globals().get("GGUF_DIR", "/content/drive/MyDrive/maize_llm/runs/gguf")
MODEL_DRIVE = f"{GGUF_DIR}/maize_llama32_1b_Q4_K_M.gguf"
MODEL_LOCAL = "/content/maize_llama32_1b_Q4_K_M.gguf"

if not os.path.exists(MODEL_LOCAL):
    print("Copiando el modelo de Drive a disco local (una sola vez)...")
    shutil.copy(MODEL_DRIVE, MODEL_LOCAL)
print(f"Modelo local listo: {os.path.getsize(MODEL_LOCAL) / 1e6:.0f} MB")

SERVER_LOG = "/content/llama_server.log"
server_proc = subprocess.Popen(
    ["/content/llama.cpp/build/bin/llama-server",
     "-m", MODEL_LOCAL,
     "-c", "2048", "--host", "127.0.0.1", "--port", "8080"],
    stdout=open(SERVER_LOG, "w"), stderr=subprocess.STDOUT,
)
print("Arrancando servidor, PID:", server_proc.pid)

# =========================================================================
# Celda 10 — Esperar al servidor, con diagnostico automatico si algo falla.
# Hasta 6 minutos (90 intentos x 4 s): la primera carga de un modelo puede tardar.
# =========================================================================
import requests

listo = False
for intento in range(90):
    if server_proc.poll() is not None:
        print(f"El proceso del servidor termino solo (codigo {server_proc.returncode}). Log:")
        print(open(SERVER_LOG).read()[-3000:])
        break
    try:
        if requests.get("http://127.0.0.1:8080/health", timeout=2).status_code == 200:
            listo = True
            break
    except Exception:
        pass
    if intento % 10 == 0:
        print(f"Esperando... {intento * 4} s")
    time.sleep(4)

if listo:
    print("Servidor listo")
else:
    print("No quedo listo en 6 minutos. Ultimas lineas del log:")
    print(open(SERVER_LOG).read()[-3000:])

# =========================================================================
# Celda 10b (diagnostico opcional) — Confirmar la ruta del archivo de prueba.
# Ya se confirmo una vez que es esta ruta; se deja el buscador por si cambia:
#   /content/drive/MyDrive/maize_llm/runs/colab_sft_v1_llama32_1b/sft_generaciones_test.jsonl
# =========================================================================
import subprocess

encontrados = subprocess.run(
    ["find", "/content/drive/MyDrive", "-ipath", "*sft_v1*test.jsonl"],
    capture_output=True, text=True,
).stdout.strip().splitlines()

for ruta in encontrados:
    print("Candidato:", ruta)

# =========================================================================
# Celda 11 — Generar respuesta con el GGUF cuantizado para cada conversacion
# del test, y comparar contra la respuesta original (sin cuantizar) que ya
# trae el archivo sft_generaciones_test.jsonl.
# =========================================================================
import json, re, datetime

SFT_TEST_PATH = "/content/drive/MyDrive/maize_llm/runs/colab_sft_v1_llama32_1b/sft_generaciones_test.jsonl"
RESULTS_PATH  = f"/content/drive/MyDrive/maize_llm/runs/eval_gguf_q4km_{datetime.date.today()}.jsonl"

QUESTION_FIELD = "pregunta"
LEVEL_FIELD    = "nivel"   # valores: agricultor / tecnico / cientifico (iguales en ES y EN)
LANG_FIELD     = "lang"    # valores: es / en

SYSTEM_PROMPTS = {
    ("es", "agricultor"): "Eres un asistente de sanidad vegetal para el cultivo de maiz en Colombia y Estados Unidos. Responde para un agricultor: lenguaje sencillo, frases cortas y pasos practicos, sin tecnicismos. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el pais de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("es", "tecnico"): "Eres un asistente de sanidad vegetal para el cultivo de maiz en Colombia y Estados Unidos. Responde para un tecnico o agronomo: momentos de aplicacion, umbrales, metodos y manejo integrado. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el pais de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("es", "cientifico"): "Eres un asistente de sanidad vegetal para el cultivo de maiz en Colombia y Estados Unidos. Responde para un investigador: mecanismos, evidencia, condiciones y limitaciones de los estudios. No inventes dosis, cifras ni productos. Si mencionas un producto o ingrediente activo, indica el pais de registro (ICA en Colombia, EPA en Estados Unidos) y que siempre se debe seguir la etiqueta registrada. Termina citando la fuente.",
    ("en", "agricultor"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a farmer: plain language, short sentences and practical steps, no jargon. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
    ("en", "tecnico"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a technician or agronomist: timing, thresholds, methods and integrated management. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
    ("en", "cientifico"): "You are a plant-health assistant for maize in Colombia and the United States. Answer for a researcher: mechanisms, evidence, conditions and limitations of the studies. Do not invent doses, figures or products. If you mention a product or active ingredient, state the country of registration (ICA in Colombia, EPA in the United States) and that the registered label must always be followed. End by citing the source.",
}

DOSE_RE = re.compile(r"\d+[.,]?\d*\s*(L/ha|kg/ha|g/L|%|d[ii]as|days)", re.IGNORECASE)
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
        registro = {"idx": i, "error": f"combinacion lang/level sin system prompt: {lang}/{level}"}
    else:
        try:
            respuesta = preguntar(system, pregunta)
            registro = {
                "idx": i,
                "lang": lang,
                "level": level,
                "pregunta": pregunta,
                "respuesta_gguf": respuesta,
                "respuesta_original": item.get("respuesta"),
                "referencia": item.get("referencia"),
                "cita_fuente": bool(CITA_RE.search(respuesta)),
                "cifra_posible_sin_respaldo": bool(DOSE_RE.search(respuesta)),
            }
        except Exception as e:
            registro = {"idx": i, "error": str(e)}
    resultados.append(registro)

    if i % 20 == 0 or i == len(items) - 1:
        with open(RESULTS_PATH, "w", encoding="utf-8") as out:
            for r_ in resultados:
                print(json.dumps(r_, ensure_ascii=False), file=out)
        print(f"{i + 1}/{len(items)} procesadas - guardado en {RESULTS_PATH}")

print("Evaluacion terminada. Resultados en:", RESULTS_PATH)

# =========================================================================
# Celda 12 — Resumen rapido y cierre del servidor
# =========================================================================
import pandas as pd

df = pd.DataFrame([r for r in resultados if "error" not in r])
if len(df):
    print(df.groupby(["lang", "level"])[["cita_fuente", "cifra_posible_sin_respaldo"]].mean())
else:
    print("No hay resultados validos para resumir; revisa los errores en", RESULTS_PATH)

server_proc.terminate()
print("Servidor detenido.")
