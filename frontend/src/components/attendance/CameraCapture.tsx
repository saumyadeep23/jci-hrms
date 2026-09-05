import { useCallback, useEffect, useRef, useState } from 'react'
import { Camera, RefreshCw, VideoOff } from 'lucide-react'
import { SecondaryButton } from '../common/ui'

interface CameraCaptureProps {
  onCapture: (dataUrl: string) => void
  captured: string | null
  onRetake: () => void
}

/** Front-facing camera capture with an animated oval face-guidance frame, per Phase 12 spec. */
export function CameraCapture({ onCapture, captured, onRetake }: CameraCaptureProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [ready, setReady] = useState(false)

  const stopStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
  }, [])

  useEffect(() => {
    if (captured) return
    let cancelled = false

    async function start() {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
          audio: false,
        })
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        streamRef.current = stream
        if (videoRef.current) {
          videoRef.current.srcObject = stream
          await videoRef.current.play()
        }
        setReady(true)
        setError(null)
      } catch {
        setError('Camera access was denied or is unavailable on this device.')
      }
    }

    start()
    return () => {
      cancelled = true
      stopStream()
    }
  }, [captured, stopStream])

  function handleCapture() {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas) return
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.translate(canvas.width, 0)
    ctx.scale(-1, 1) // mirror to match the on-screen preview
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    onCapture(canvas.toDataURL('image/jpeg', 0.85))
    stopStream()
  }

  if (captured) {
    return (
      <div className="relative mx-auto w-full max-w-sm overflow-hidden rounded-xl border border-slate-200">
        <img src={captured} alt="Captured punch selfie" className="w-full scale-x-[-1]" />
        <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-3">
          <SecondaryButton onClick={onRetake} className="bg-white/90">
            <RefreshCw size={14} /> Retake
          </SecondaryButton>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-sm">
      <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-slate-900">
        {error ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-slate-300">
            <VideoOff size={28} />
            <p className="px-6 text-center text-xs">{error}</p>
          </div>
        ) : (
          <>
            <video ref={videoRef} muted playsInline className="h-full w-full scale-x-[-1] object-cover" />
            {/* Animated oval face-guidance frame */}
            <svg viewBox="0 0 200 200" className="pointer-events-none absolute inset-0 h-full w-full">
              <ellipse
                cx="100"
                cy="95"
                rx="55"
                ry="72"
                fill="none"
                stroke="#d4a373"
                strokeWidth="3"
                strokeDasharray="10 6"
                className="origin-center animate-[spin_8s_linear_infinite]"
              />
              <ellipse cx="100" cy="95" rx="55" ry="72" fill="none" stroke="white" strokeWidth="1.5" strokeOpacity="0.6" />
            </svg>
          </>
        )}
      </div>
      <canvas ref={canvasRef} className="hidden" />
      <button
        type="button"
        onClick={handleCapture}
        disabled={!ready || !!error}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-brand-forest px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50"
      >
        <Camera size={16} /> Capture
      </button>
      <p className="mt-2 text-center text-[11px] text-slate-400">
        Photo is captured for on-device verification only - this backend has no photo storage endpoint yet, so it is not uploaded.
      </p>
    </div>
  )
}
