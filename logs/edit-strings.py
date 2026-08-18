import re
p = r'app\src\main\res\values\strings.xml'
with open(p, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove LLM string entries + their comments
# The capture_sheet block has: capture_sheet_extract, capture_sheet_llm_*
# All of them live between <string name="capture_sheet_text_placeholder"> and
# the M1-T4: confirmation card section.
# Easiest: remove the whole <!-- v1.6.0: LLM graceful fallback --> comment + the 4 capture_sheet_llm_* strings,
# AND remove capture_sheet_extract + the <!-- M1-T4: confirmation card --> block.
content = re.sub(
    r'\s*<string name="capture_sheet_extract">[^<]*</string>\s*<string name="capture_sheet_confirm">[^<]*</string>'
    r'\s*<!-- v1\.6\.0: LLM graceful fallback.*?-->\s*<string name="capture_sheet_llm_unavailable">[^<]*</string>\s*'
    r'<string name="capture_sheet_llm_unavailable_card_title">[^<]*</string>\s*'
    r'<string name="capture_sheet_llm_unavailable_card_body">[^<]*</string>\s*'
    r'<string name="capture_sheet_llm_unavailable_save_plain">[^<]*</string>',
    '\n    <string name="capture_sheet_save">Save</string>\n'
    '    <string name="capture_sheet_add_to_calendar">Add to calendar</string>\n',
    content, flags=re.DOTALL)

# 2. Remove <!-- M1-T4: confirmation card --> block
content = re.sub(
    r'\s*<!-- M1-T4: confirmation card -->\s*'
    r'<string name="confirmation_person">[^<]*</string>\s*'
    r'<string name="confirmation_action">[^<]*</string>\s*'
    r'<string name="confirmation_instruction_text">[^<]*</string>\s*'
    r'<string name="confirmation_add_to_calendar">[^<]*</string>',
    '', content, flags=re.DOTALL)

# 3. Remove a11y_confidence_* strings + their comment
content = re.sub(
    r'\s*<string name="a11y_confidence_high">[^<]*</string>\s*'
    r'<string name="a11y_confidence_medium">[^<]*</string>\s*'
    r'<string name="a11y_confidence_low">[^<]*</string>',
    '', content)

# 4. Remove model_download_* + model_picker_* + model_qwen3_* + model_llama_* + model_gemma_* + model_phi_* + settings_section_models + settings_model_*
content = re.sub(
    r'\s*<!-- v1\.4\.2 \(F-10\): first-run on-device LLM model download.*?-->\s*'
    r'<string name="model_download_title">[^<]*</string>\s*'
    r'<string name="model_download_subtitle">[^<]*</string>\s*'
    r'<string name="model_download_button">[^<]*</string>\s*'
    r'<string name="model_download_percent">[^<]*</string>\s*'
    r'<string name="model_download_ready">[^<]*</string>\s*'
    r'<string name="model_download_size_mb">[^<]*</string>\s*'
    r'<string name="model_download_continue">[^<]*</string>\s*'
    r'<string name="model_download_failed_reason">[^<]*</string>\s*'
    r'<string name="model_download_retry">[^<]*</string>',
    '', content, flags=re.DOTALL)

content = re.sub(
    r'\s*<!-- v1\.4\.3 \(F-10\): model picker.*?-->\s*'
    r'<string name="model_picker_title">[^<]*</string>\s*'
    r'<string name="model_picker_switch">[^<]*</string>\s*'
    r'<string name="model_picker_cancel">[^<]*</string>\s*'
    r'<string name="model_picker_confirm">[^<]*</string>',
    '', content, flags=re.DOTALL)

content = re.sub(
    r'\s*<!-- v1\.4\.3 \(F-10\): one name.*?-->\s*'
    r'<string name="model_qwen3_1_7b_q4_k_m_name">[^<]*</string>\s*'
    r'<string name="model_qwen3_1_7b_q4_k_m_description">[^<]*</string>\s*'
    r'<string name="model_llama_3_2_3b_instruct_q4_k_m_name">[^<]*</string>\s*'
    r'<string name="model_llama_3_2_3b_instruct_q4_k_m_description">[^<]*</string>\s*'
    r'<string name="model_gemma_2_2b_it_q4_k_m_name">[^<]*</string>\s*'
    r'<string name="model_gemma_2_2b_it_q4_k_m_description">[^<]*</string>\s*'
    r'<string name="model_phi_3_5_mini_3_8b_q4_k_m_name">[^<]*</string>\s*'
    r'<string name="model_phi_3_5_mini_3_8b_q4_k_m_description">[^<]*</string>',
    '', content, flags=re.DOTALL)

content = re.sub(
    r'\s*<!-- v1\.5\.4: Settings.+Models section.*?-->\s*'
    r'<string name="settings_section_models">[^<]*</string>\s*'
    r'<string name="settings_model_llm">[^<]*</string>\s*'
    r'<string name="settings_model_whisper">[^<]*</string>\s*'
    r'<string name="settings_model_not_downloaded">[^<]*</string>\s*'
    r'<string name="settings_model_downloading">[^<]*</string>\s*'
    r'<string name="settings_model_ready">[^<]*</string>\s*'
    r'<string name="settings_model_ready_short">[^<]*</string>\s*'
    r'<string name="settings_model_download_short">[^<]*</string>',
    '', content, flags=re.DOTALL)

# 5. Update text strings
content = content.replace(
    "Type a free-form note. We\\'ll extract the instruction.",
    "Type a free-form note.")
content = content.replace(
    "Erase all data on this phone: every person, every instruction, every tag, and the on-device model files. This cannot be undone.",
    "Erase all data on this phone: every person, every instruction, and every tag. This cannot be undone.")
content = content.replace(
    "This deletes every person, every instruction, every tag, and the on-device model files. There is no backup. This cannot be undone.",
    "This deletes every person, every instruction, and every tag. There is no backup. This cannot be undone.")
content = content.replace(
    "Recording your instruction. Tap Stop when done.",
    "Speak your note. Tap Stop when done.")
content = content.replace(
    "Add a person first to capture instructions.",
    "Add a person first to attach this note to.")

# Save
with open(p, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done. New file size:', len(content))
