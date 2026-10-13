package main

import (
	"flag"
	"log"
	"os"

	"linenexus-edge/internal/app"
)

func main() {
	cfgPath := flag.String("config", "configs/config.yaml", "配置文件路径")
	flag.Parse()

	if err := app.Run(*cfgPath); err != nil {
		log.Printf("edge 退出: %v", err)
		os.Exit(1)
	}
}
