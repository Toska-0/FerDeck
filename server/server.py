from __future__ import annotations
import asyncio, json, os, secrets, socket, subprocess
import psutil
import websockets

PORT = int(os.getenv('FERDECK_PORT', '8765'))
PIN = os.getenv('FERDECK_PIN') or f'{secrets.randbelow(1_000_000):06d}'
COMMANDS = {
    'notepad': ['notepad.exe'],
    'calculator': ['calc.exe'],
    'steam': [r'C:\Program Files (x86)\Steam\steam.exe'],
    'browser': ['cmd', '/c', 'start', '', 'https://www.google.com'],
}
ALLOWED_KEYS = {'ctrl','shift','alt','win','enter','esc','tab','space','f1','f2','f3','f4','f5','f6','f7','f8','f9','f10','f11','f12','up','down','left','right','home','end','delete','backspace','a','c','v','x','z','m','d','r','l'}

def get_ip():
    sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
    try:
        sock.connect(('8.8.8.8',80)); return sock.getsockname()[0]
    except OSError: return '127.0.0.1'
    finally: sock.close()

def gpu_usage():
    try:
        raw=subprocess.check_output(['nvidia-smi','--query-gpu=utilization.gpu','--format=csv,noheader,nounits'],text=True,timeout=2,stderr=subprocess.DEVNULL)
        return float(raw.strip().splitlines()[0])
    except (OSError,subprocess.SubprocessError,ValueError,IndexError): return -1.0

def hotkey(keys):
    import keyboard
    parts=[x.strip().lower() for x in keys.split('+') if x.strip()]
    if not parts or len(parts)>5 or any(x not in ALLOWED_KEYS for x in parts): raise ValueError('Недопустимая комбинация клавиш')
    keyboard.send('+'.join(parts))

def run_macro(sequence):
    import keyboard
    # Format: ctrl+shift+m;wait:300;alt+f4
    steps=[s.strip() for s in sequence.split(';') if s.strip()]
    if len(steps)>20: raise ValueError('Слишком много шагов макроса')
    for step in steps:
        if step.startswith('wait:'):
            ms=int(step.split(':',1)[1])
            if ms<0 or ms>5000: raise ValueError('Задержка должна быть 0..5000 мс')
            import time; time.sleep(ms/1000)
        else: hotkey(step)

def media(action):
    import keyboard
    mapping={'play_pause':'play/pause media','next':'next track','prev':'previous track','mute':'volume mute'}
    if action not in mapping: raise ValueError('Неизвестная media-команда')
    keyboard.press_and_release(mapping[action])

def set_volume(value):
    value=max(0.0,min(1.0,float(value)))
    try:
        from pycaw.pycaw import AudioUtilities
        device=AudioUtilities.GetSpeakers()
        device.EndpointVolume.SetMasterVolumeLevelScalar(value,None)
    except Exception:
        # Fallback: one safe media-key action; exact scalar volume requires pycaw.
        import keyboard
        keyboard.press_and_release('volume mute' if value==0 else ('volume up' if value>=0.5 else 'volume down'))

async def send_telemetry(ws):
    while True:
        await ws.send(json.dumps({'type':'telemetry','cpu':psutil.cpu_percent(interval=None),'ram':psutil.virtual_memory().percent,'gpu':gpu_usage()}))
        await asyncio.sleep(1)

async def handler(ws):
    authenticated=False; telemetry_task=None
    try:
        async for raw in ws:
            try: msg=json.loads(raw)
            except (json.JSONDecodeError,TypeError): continue
            if not authenticated:
                if msg.get('type')=='auth' and secrets.compare_digest(str(msg.get('pin','')),PIN):
                    authenticated=True; telemetry_task=asyncio.create_task(send_telemetry(ws)); await ws.send(json.dumps({'type':'auth_ok'}))
                else:
                    await ws.close(code=4001,reason='PIN rejected'); return
                continue
            try:
                kind=msg.get('type')
                if kind=='hotkey': hotkey(str(msg.get('keys','')))
                elif kind=='macro': run_macro(str(msg.get('keys','')))
                elif kind=='media': media(str(msg.get('action','')))
                elif kind=='volume': set_volume(msg.get('value',0))
                elif kind=='launch':
                    command=str(msg.get('command',''))
                    if command not in COMMANDS: raise ValueError('Команда отсутствует в белом списке')
                    subprocess.Popen(COMMANDS[command],shell=False)
                else: raise ValueError('Неизвестный тип команды')
                await ws.send(json.dumps({'type':'ack','ok':True}))
            except Exception as exc:
                await ws.send(json.dumps({'type':'ack','ok':False,'error':str(exc)[:180]}))
    finally:
        if telemetry_task: telemetry_task.cancel()

async def main():
    psutil.cpu_percent(interval=None)
    print(f'FerDeck server: ws://{get_ip()}:{PORT} | PIN: {PIN}')
    print('Смартфон и ПК должны быть в одной Wi-Fi сети. Ctrl+C — выход.')
    async with websockets.serve(handler,'0.0.0.0',PORT,ping_interval=20,max_size=65536): await asyncio.Future()

if __name__=='__main__':
    try: asyncio.run(main())
    except KeyboardInterrupt: print('Server stopped')
