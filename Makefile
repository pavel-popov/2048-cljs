
build-css:
	npm run tw

build-js:
	npx shadow-cljs release app

DATE=$(shell date)
push-to-gh:
	cd target && \
	rm -rf .git && \
	git init && \
	git remote add origin git@github.com:pavel-popov/2048-cljs.git && \
	git fetch origin && \
	git checkout -b temp && \
	git reset gh-pages && \
	git checkout gh-pages && \
	git branch -d temp && \
	git add css *.html *.js && \
	git commit -m "Release as of $(DATE)" && \
	git push origin gh-pages

deploy:  # deploys a production build to Github Pages
	make build-js
	make build-css NODE_ENV=production


gh-pages:
	git subtree add --prefix gh-pages git@github.com:pavel-popov/2048-cljs.git gh-pages --squash
