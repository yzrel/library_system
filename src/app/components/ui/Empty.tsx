import {BookOpen} from "lucide-react";

export function Empty({message}: {message: string}) { return <div className="empty"><BookOpen size={34}/><p>{message}</p></div>; }
