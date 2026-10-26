import {useCallback, useEffect, useRef, useState} from "react"; import {BookOpen, Camera, CheckCircle2, QrCode, Star} from "lucide-react"; import {BrowserQRCodeReader} from "@zxing/browser"; import {toast} from "sonner";
import {api, json} from "@/app/api"; import type {AttendanceSuggestion, AttendanceVisit, CheckInResult, FeedbackRating, TodayAttendance, VisitPurpose} from "@/app/api";
import {ATTENDANCE_PURPOSES} from "@/app/types";
import {Button} from "@/app/components/ui/Button"; import {SelectField} from "@/app/components/ui/Field";

export function AttendanceKiosk() {
  const [mode,setMode]=useState<"MANUAL"|"QR">("MANUAL");
  const [purpose,setPurpose]=useState<VisitPurpose|"">("");
  const [identifier,setIdentifier]=useState("");
  const [displayValue,setDisplayValue]=useState("");
  const [suggestions,setSuggestions]=useState<AttendanceSuggestion[]>([]);
  const [openSuggest,setOpenSuggest]=useState(false);
  const [activeSuggest,setActiveSuggest]=useState(-1);
  const [result,setResult]=useState<CheckInResult|null>(null);
  const [busy,setBusy]=useState(false);
  const [scanning,setScanning]=useState(false);
  const [today,setToday]=useState<TodayAttendance|null>(null);
  const [todayLoading,setTodayLoading]=useState(true);
  const videoRef=useRef<HTMLVideoElement>(null);
  const controlsRef=useRef<{stop:()=>void}|null>(null);
  const processingRef=useRef(false);
  const suggestBoxRef=useRef<HTMLDivElement>(null);

  const stopScan=useCallback(()=>{controlsRef.current?.stop();controlsRef.current=null;setScanning(false)},[]);
  const loadToday=useCallback(async()=>{
    try{setToday(await api<TodayAttendance>("/attendance/today"))}
    catch{/* keep last good list */}
    finally{setTodayLoading(false)}
  },[]);
  useEffect(()=>()=>controlsRef.current?.stop(),[]);
  useEffect(()=>{void loadToday(); const timer=window.setInterval(()=>void loadToday(),30000); return ()=>window.clearInterval(timer)},[loadToday]);
  useEffect(()=>{
    const onDoc= (event: MouseEvent) => {
      if(!suggestBoxRef.current?.contains(event.target as Node)) setOpenSuggest(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  },[]);
  useEffect(()=>{
    if(mode!=="MANUAL"){setSuggestions([]);setOpenSuggest(false);return}
    const query=displayValue.trim();
    if(!query){setSuggestions([]);setOpenSuggest(false);setActiveSuggest(-1);return}
    const timer=window.setTimeout(()=>{
      void api<AttendanceSuggestion[]>(`/attendance/suggest?q=${encodeURIComponent(query)}`)
        .then(rows=>{setSuggestions(rows);setOpenSuggest(rows.length>0);setActiveSuggest(rows.length?0:-1)})
        .catch(()=>{setSuggestions([]);setOpenSuggest(false);setActiveSuggest(-1)});
    },160);
    return ()=>window.clearTimeout(timer);
  },[displayValue,mode]);

  const reset=useCallback(()=>{stopScan();setResult(null);setIdentifier("");setDisplayValue("");setSuggestions([]);setOpenSuggest(false);setActiveSuggest(-1);setPurpose("");setMode("MANUAL");processingRef.current=false;void loadToday()},[stopScan,loadToday]);
  const pickSuggestion=(row:AttendanceSuggestion)=>{
    setIdentifier(row.identifier);
    setDisplayValue(`${row.name} · ${row.identifier}`);
    setSuggestions([]);setOpenSuggest(false);setActiveSuggest(-1);
  };
  const checkIn=async(value:string,entryMethod:"MANUAL"|"QR")=>{
    if(!purpose){toast.error("Choose a purpose first");return}
    if(!value.trim()||processingRef.current)return;
    processingRef.current=true;setBusy(true);setOpenSuggest(false);
    try{
      const data=await api<CheckInResult>("/attendance/check-in",json("POST",{identifier:value.trim(),purpose,entryMethod}));
      stopScan();setResult(data);toast.success("Attendance recorded");void loadToday();
    }catch(error){toast.error((error as Error).message);processingRef.current=false}
    finally{setBusy(false)}
  };
  const startScan=async()=>{
    if(!purpose){toast.error("Choose a purpose before scanning");return}
    setMode("QR");setScanning(true);processingRef.current=false;
    try{
      const reader=new BrowserQRCodeReader();
      controlsRef.current=await reader.decodeFromVideoDevice(undefined,videoRef.current!,result=>{
        if(result&&!processingRef.current){const text=result.getText();setIdentifier(text);setDisplayValue(text);void checkIn(text,"QR")}
      });
    }catch(error){setScanning(false);toast.error(error instanceof Error?error.message:"Camera is unavailable. Type your ID instead.")}
  };
  const submitFeedback=async(rating:FeedbackRating)=>{
    if(!result)return;
    setBusy(true);
    try{await api("/attendance/feedback",json("POST",{visitId:result.visitId,rating}));toast.success("Thank you for your feedback");setTimeout(reset,1200)}
    catch(error){toast.error((error as Error).message)}
    finally{setBusy(false)}
  };
  const onManualKeyDown=(event: React.KeyboardEvent<HTMLInputElement>)=>{
    if(!openSuggest||suggestions.length===0)return;
    if(event.key==="ArrowDown"){event.preventDefault();setActiveSuggest(i=>(i+1)%suggestions.length)}
    else if(event.key==="ArrowUp"){event.preventDefault();setActiveSuggest(i=>(i<=0?suggestions.length-1:i-1))}
    else if(event.key==="Enter"&&activeSuggest>=0){event.preventDefault();pickSuggestion(suggestions[activeSuggest])}
    else if(event.key==="Escape"){setOpenSuggest(false)}
  };

  return <main className="kiosk-page">
    <header className="kiosk-header"><a href="/" className="brand dark"><span className="brand-mark">V</span><span><strong>VFGNHS Library</strong><small>Attendance Portal</small></span></a><a href="/" className="btn btn-secondary">Back to catalog</a></header>
    <section className="kiosk-card">
      {!result?<><div className="kiosk-title"><QrCode/><span className="eyebrow">Library attendance</span><h1>Welcome to the library</h1><p>Choose your purpose, then type your name or ID, or scan your QR code.</p></div>
        <SelectField label="Purpose of visit" value={purpose} onChange={e=>setPurpose(e.target.value as VisitPurpose)} required><option value="">Choose a purpose</option>{ATTENDANCE_PURPOSES.map(item=><option key={item.value} value={item.value}>{item.label}</option>)}</SelectField>
        <div className="tabs kiosk-tabs"><button className={mode==="MANUAL"?"active":""} onClick={()=>{stopScan();setMode("MANUAL")}}>Type ID</button><button className={mode==="QR"?"active":""} onClick={()=>setMode("QR")}>Scan QR</button></div>
        {mode==="MANUAL"?<form className="form-grid" onSubmit={e=>{e.preventDefault();
          const chosen=activeSuggest>=0&&suggestions[activeSuggest]?suggestions[activeSuggest].identifier
            :suggestions.length===1?suggestions[0].identifier:(identifier||displayValue);
          void checkIn(chosen,"MANUAL")}}>
          <div className="suggest-field" ref={suggestBoxRef}>
            <label className="field"><span>Name, school ID, LRN, employee ID, or card number</span>
              <input value={displayValue} onChange={e=>{setDisplayValue(e.target.value);setIdentifier(e.target.value);setOpenSuggest(true)}}
                onFocus={()=>setOpenSuggest(suggestions.length>0)} onKeyDown={onManualKeyDown}
                autoFocus autoComplete="off" required aria-autocomplete="list" aria-expanded={openSuggest} aria-controls="attendance-suggestions"/>
            </label>
            {openSuggest&&suggestions.length>0&&<ul id="attendance-suggestions" className="suggest-list" role="listbox">
              {suggestions.map((row,index)=><li key={row.identifier}><button type="button" role="option" aria-selected={index===activeSuggest}
                className={index===activeSuggest?"active":""} onMouseDown={e=>e.preventDefault()} onClick={()=>pickSuggestion(row)}>
                <strong>{row.name}</strong><span>{row.gradeSection||row.role} · {row.schoolId} · {row.identifier}</span>
              </button></li>)}
            </ul>}
          </div>
          <Button disabled={busy||!purpose}>{busy?"Recording…":"Record attendance"}</Button>
        </form>:
          <div className="scanner-panel"><video ref={videoRef} muted playsInline aria-label="QR camera preview"/>{scanning?<Button variant="secondary" onClick={stopScan}>Stop camera</Button>:<Button onClick={()=>void startScan()} disabled={!purpose}><Camera size={18}/> Start camera</Button>}<p>Point the camera at the QR code. You can switch to Type ID if camera access is unavailable.</p></div>}
      </>:<div className="checkin-success"><CheckCircle2/><span className="eyebrow">Attendance recorded</span><h1>Welcome, {result.name}!</h1><p>{new Date(result.checkedInAt).toLocaleString("en-PH")} · {ATTENDANCE_PURPOSES.find(x=>x.value===result.purpose)?.label}</p>
        <div className="feedback-box"><h2>How satisfied are you with our library service?</h2><div className="feedback-options"><Button disabled={busy} onClick={()=>void submitFeedback("SATISFIED")}><Star/> Satisfied</Button><Button disabled={busy} variant="secondary" onClick={()=>void submitFeedback("NEUTRAL")}>Neutral</Button><Button disabled={busy} variant="secondary" onClick={()=>void submitFeedback("NOT_SATISFIED")}>Not satisfied</Button></div><button className="skip-feedback" onClick={reset}>Skip feedback / Next visitor</button></div>
      </div>}
    </section>
    <section className="kiosk-day-log" aria-labelledby="today-attendance-title">
      <div className="kiosk-day-head">
        <div><span className="eyebrow">Today's logbook</span><h2 id="today-attendance-title">Attendance as of today</h2>
          <p>{today?new Date(today.date+"T00:00:00").toLocaleDateString("en-PH",{weekday:"long",month:"long",day:"numeric",year:"numeric"}):"Loading today's visits…"}</p></div>
        <strong>{today?.count??0} {today?.count===1?"visitor":"visitors"}</strong>
      </div>
      {todayLoading&&!today?<div className="loading">Loading today's attendance…</div>
        :!today?.visits.length?<div className="empty"><BookOpen size={30}/><p>No library visits recorded yet today.</p></div>
        :<><div className="table-wrap kiosk-day-table"><table><thead><tr><th>Time</th><th>Name</th><th>Grade and section</th><th>Purpose</th></tr></thead>
          <tbody>{today.visits.map((row:AttendanceVisit)=><tr key={row.id}><td>{new Date(row.checkedInAt).toLocaleTimeString("en-PH",{hour:"numeric",minute:"2-digit"})}</td><td><strong>{row.name}</strong><small>{row.role}</small></td><td>{row.gradeSection||"—"}</td><td>{ATTENDANCE_PURPOSES.find(x=>x.value===row.purpose)?.label||row.purpose}</td></tr>)}</tbody></table></div>
          <div className="kiosk-day-cards">{today.visits.map(row=><article key={row.id}><div><strong>{row.name}</strong><span>{new Date(row.checkedInAt).toLocaleTimeString("en-PH",{hour:"numeric",minute:"2-digit"})}</span></div><p>{row.gradeSection||row.role}</p><footer><span>{ATTENDANCE_PURPOSES.find(x=>x.value===row.purpose)?.label||row.purpose}</span><span className="badge">{row.entryMethod}</span></footer></article>)}</div></>}
    </section>
  </main>;
}
