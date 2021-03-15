
build-css:
	npm run tw

build-js:
	npx shadow-cljs release app

deploy:  # deploys a production build to Github Pages
	make build-js
	make build-css NODE_ENV=production
